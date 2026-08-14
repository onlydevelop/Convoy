# Build & deploy for Convoy, a fleet telemetry ingestion system.
#
# Requires: gradle wrapper (bundled), docker, envsubst (gettext), git (image
# tagging), ssh (ENV=cloud). ENV=local also needs local kubectl/helm; ENV=cloud
# runs kubectl/helm remotely over SSH, no local install needed (setup-cloud
# installs k3s + Helm on the VM itself).
#
# Usage:
#   make build SERVICE=ingestion-service          # build one service's jar
#   make build-all                                 # build all services
#   make image SERVICE=ingestion-service            # docker build one image
#   make push SERVICE=ingestion-service              # docker push one image
#   make ghcr-secret GHCR_OWNER=... GHCR_TOKEN=...   # imagePullSecret, needed if GHCR packages are private
#   make deploy-service SERVICE=ingestion-service ENV=local   # roll out one service
#   make deploy-services ENV=cloud                   # roll out all app services
#   make deploy-infra ENV=local                      # namespace + kafka + topic
#   make redeploy-infra ENV=local                     # tear down + re-deploy the infra stack (kafka + topic)
#   make redeploy-app ENV=local GHCR_OWNER=...        # tear down + rebuild/push/deploy all app services
#   make bootstrap ENV=cloud                         # one-time: infra + initial app manifests
#   make deploy-all ENV=cloud                        # infra + all services
#   make rollback SERVICE=ingestion-service ENV=cloud
#   make status ENV=local                             # nodes/deployments/pods/svc
#   make health ENV=local                             # hit /health on both services
#   make teardown ENV=local                          # delete everything (services, infra, namespace)
#   make setup-cloud ENV=cloud DEPLOY_USER=... DEPLOY_HOST=...  # install k3s+helm on a fresh VM
#
# ENV=local (default) runs kubectl/helm directly against the current context
# (e.g. k3d). ENV=cloud runs them over SSH on the target VM, per
# docs/cd-pipeline-spec.md. `bootstrap ENV=cloud` runs setup-cloud first
# automatically, so a fresh VM only needs SSH access + `make bootstrap`.

SHELL := /bin/bash

SERVICES := ingestion-service load-generator
NAMESPACE := convoy

ENV ?= local
TAG ?= $(shell git rev-parse --short HEAD 2>/dev/null || echo dev)

GHCR_OWNER ?=
GHCR_REPO ?= convoy
GHCR_USER ?= $(GHCR_OWNER)
GHCR_TOKEN ?=
REGISTRY := ghcr.io/$(GHCR_OWNER)/$(GHCR_REPO)

DEPLOY_USER ?=
DEPLOY_HOST ?=

# k3s writes its kubeconfig to /etc/rancher/k3s/k3s.yaml; setup-cloud installs
# k3s with K3S_KUBECONFIG_MODE=644 so DEPLOY_USER can read it without sudo.
ifeq ($(ENV),cloud)
  KUBECTL := ssh $(DEPLOY_USER)@$(DEPLOY_HOST) KUBECONFIG=/etc/rancher/k3s/k3s.yaml kubectl
  HELM := ssh $(DEPLOY_USER)@$(DEPLOY_HOST) KUBECONFIG=/etc/rancher/k3s/k3s.yaml helm
  BOOTSTRAP_DEPS := setup-cloud helm-repo deploy-infra deploy-init
else
  KUBECTL := kubectl
  HELM := helm
  BOOTSTRAP_DEPS := helm-repo deploy-infra deploy-init
endif

.PHONY: help check-registry check-service check-cloud setup-cloud \
        build build-all image image-all push push-all \
        helm-repo namespace ghcr-secret deploy-infra deploy-init \
        deploy-service deploy-services deploy-all rollback status health bootstrap clean \
        redeploy-infra redeploy-app \
        teardown-services teardown-infra teardown

help: ## Show this help
	@grep -hE '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | sed 's/:.*## /\t- /'

check-registry:
ifeq ($(GHCR_OWNER),)
	$(error GHCR_OWNER is not set, e.g. make image SERVICE=ingestion-service GHCR_OWNER=youruser)
endif

check-service:
ifeq ($(SERVICE),)
	$(error SERVICE is not set, e.g. SERVICE=ingestion-service)
endif

check-cloud:
ifneq ($(ENV),cloud)
	$(error This target only applies to ENV=cloud)
endif
ifeq ($(DEPLOY_HOST),)
	$(error DEPLOY_HOST is not set)
endif
ifeq ($(DEPLOY_USER),)
	$(error DEPLOY_USER is not set)
endif

## --- cloud VM setup ---------------------------------------------------

setup-cloud: check-cloud ## Install k3s + Helm on a fresh cloud VM over SSH (one-time, idempotent)
	ssh $(DEPLOY_USER)@$(DEPLOY_HOST) 'command -v k3s >/dev/null 2>&1 || \
		(curl -sfL https://get.k3s.io | K3S_KUBECONFIG_MODE="644" sh -)'
	ssh $(DEPLOY_USER)@$(DEPLOY_HOST) 'command -v helm >/dev/null 2>&1 || \
		(curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash)'
	$(KUBECTL) wait --for=condition=Ready node --all --timeout=120s

## --- build (individual project) -------------------------------------------

build: check-service ## Build one service's jar (SERVICE=<name>)
	./gradlew :services:$(SERVICE):build

build-all: ## Build all services
	./gradlew build

## --- image / push -----------------------------------------------------

image: check-service check-registry ## Docker build one service's image (SERVICE=<name>)
	docker build -f services/$(SERVICE)/Dockerfile -t $(REGISTRY)/$(SERVICE):$(TAG) .

image-all: check-registry ## Docker build all service images
	@for s in $(SERVICES); do $(MAKE) image SERVICE=$$s; done

push: check-service check-registry ## Docker push one service's image (SERVICE=<name>)
	docker push $(REGISTRY)/$(SERVICE):$(TAG)

push-all: check-registry ## Docker push all service images
	@for s in $(SERVICES); do $(MAKE) push SERVICE=$$s; done

## --- infra stack (kafka, future: database, ...) ------------------------

helm-repo: ## Add/update the Bitnami Helm repo
	$(HELM) repo add bitnami https://charts.bitnami.com/bitnami
	$(HELM) repo update

namespace: ## Apply the convoy namespace
	cat infra/k8s/namespace.yaml | $(KUBECTL) apply -f -

ghcr-secret: check-registry namespace ## Create/update the ghcr-pull imagePullSecret (needed if the GHCR packages are private)
ifeq ($(GHCR_TOKEN),)
	$(error GHCR_TOKEN is not set, e.g. make ghcr-secret GHCR_OWNER=youruser GHCR_TOKEN=ghp_xxx -- a PAT with read:packages scope)
endif
	$(KUBECTL) create secret docker-registry ghcr-pull \
		--docker-server=ghcr.io \
		--docker-username=$(GHCR_USER) \
		--docker-password=$(GHCR_TOKEN) \
		--namespace $(NAMESPACE) \
		--dry-run=client -o yaml | $(KUBECTL) apply -f -

deploy-infra: namespace ## Deploy the infra stack (kafka + topic)
	cat infra/kafka/values.yaml | $(HELM) upgrade --install kafka bitnami/kafka --namespace $(NAMESPACE) -f -
	cat infra/kafka/topic-init-job.yaml | $(KUBECTL) apply -f -

redeploy-infra: ## Tear down and re-deploy the infra stack (kafka + topic)
	$(MAKE) teardown-infra ENV=$(ENV) DEPLOY_USER=$(DEPLOY_USER) DEPLOY_HOST=$(DEPLOY_HOST)
	$(MAKE) deploy-infra ENV=$(ENV) DEPLOY_USER=$(DEPLOY_USER) DEPLOY_HOST=$(DEPLOY_HOST)

## --- service stack (ingestion_service, load_generator) -----------------

deploy-init: check-registry namespace ## One-time: create initial Deployments/Services for all app services
	INGESTION_SERVICE_IMAGE=$(REGISTRY)/ingestion-service:$(TAG) \
		envsubst < services/ingestion-service/k8s/deployment.yaml | $(KUBECTL) apply -f -
	cat services/ingestion-service/k8s/service.yaml | $(KUBECTL) apply -f -
	LOAD_GENERATOR_IMAGE=$(REGISTRY)/load-generator:$(TAG) \
		envsubst < services/load-generator/k8s/deployment.yaml | $(KUBECTL) apply -f -

deploy-service: check-service check-registry ## Roll out a new image for one service (SERVICE=<name>)
	$(KUBECTL) set image deployment/$(SERVICE) $(SERVICE)=$(REGISTRY)/$(SERVICE):$(TAG) -n $(NAMESPACE)
	$(KUBECTL) rollout status deployment/$(SERVICE) -n $(NAMESPACE)

deploy-services: check-registry ## Roll out a new image for all app services
	@for s in $(SERVICES); do $(MAKE) deploy-service SERVICE=$$s ENV=$(ENV) TAG=$(TAG) GHCR_OWNER=$(GHCR_OWNER) GHCR_REPO=$(GHCR_REPO); done

redeploy-app: check-registry ## Tear down, rebuild, and re-deploy all app services (code changes)
	$(MAKE) teardown-services ENV=$(ENV) DEPLOY_USER=$(DEPLOY_USER) DEPLOY_HOST=$(DEPLOY_HOST)
	$(MAKE) build-all
	$(MAKE) image-all GHCR_OWNER=$(GHCR_OWNER) GHCR_REPO=$(GHCR_REPO) TAG=$(TAG)
	$(MAKE) push-all GHCR_OWNER=$(GHCR_OWNER) GHCR_REPO=$(GHCR_REPO) TAG=$(TAG)
	$(MAKE) deploy-init ENV=$(ENV) GHCR_OWNER=$(GHCR_OWNER) GHCR_REPO=$(GHCR_REPO) TAG=$(TAG) DEPLOY_USER=$(DEPLOY_USER) DEPLOY_HOST=$(DEPLOY_HOST)

rollback: check-service ## Undo the last rollout for one service (SERVICE=<name>)
	$(KUBECTL) rollout undo deployment/$(SERVICE) -n $(NAMESPACE)

status: ## Show cluster/namespace status: nodes, deployments, pods, services
	$(KUBECTL) get nodes -o wide
	$(KUBECTL) get deployments,pods,svc -n $(NAMESPACE) -o wide

health: ## Hit the /health endpoint on both services via kubectl exec
	@echo "== ingestion-service (:8080/health) =="
	@$(KUBECTL) exec -n $(NAMESPACE) deploy/ingestion-service -- wget -qO- http://localhost:8080/health && echo
	@echo "== load-generator (:8081/health) =="
	@$(KUBECTL) exec -n $(NAMESPACE) deploy/load-generator -- wget -qO- http://localhost:8081/health && echo

## --- whole stack ---------------------------------------------------------

bootstrap: $(BOOTSTRAP_DEPS) ## One-time cluster bootstrap: (cloud: install k3s+helm) + infra + initial app manifests

deploy-all: deploy-infra deploy-services ## Deploy infra stack + all app services

## --- teardown --------------------------------------------------------------

teardown-services: ## Delete app service Deployments/Services
	cat services/ingestion-service/k8s/service.yaml | $(KUBECTL) delete -f - --ignore-not-found
	@for s in $(SERVICES); do $(KUBECTL) delete deployment $$s -n $(NAMESPACE) --ignore-not-found; done

teardown-infra: ## Delete the infra stack (kafka + topic job)
	$(KUBECTL) delete -f infra/kafka/topic-init-job.yaml --ignore-not-found
	-$(HELM) uninstall kafka --namespace $(NAMESPACE)

teardown: teardown-services teardown-infra ## Tear down the whole stack, including the namespace
	$(KUBECTL) delete namespace $(NAMESPACE) --ignore-not-found

clean: ## Clean all Gradle build outputs
	./gradlew clean
