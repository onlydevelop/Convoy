# Convoy

A high-volume ingestion pipeline for a fleet of transport vehicles reporting
geolocation, velocity, and related telemetry via REST, buffered through
Kafka. Design docs live in [docs/](docs/):

- [docs/spec.md](docs/spec.md) — ingestion API + Kafka design
- [docs/implementation-spec.md](docs/implementation-spec.md) — k3s low-level design
- [docs/cd-pipeline-spec.md](docs/cd-pipeline-spec.md) — GitHub → cloud VM CD pipeline

Components: `services/ingestion-service` (REST → Kafka producer),
`services/load-generator` (simulates the vehicle fleet), and `infra/kafka`
(Kafka Helm values + topic definition). All build/deploy operations go
through the root [Makefile](Makefile) — run `make help` for the full target
list.

## Prerequisites

- Docker
- `envsubst` (part of `gettext`; on macOS: `brew install gettext && brew link --force gettext`)
- A JDK 21 for local IDE use (the Gradle wrapper handles the actual build;
  no local Gradle install needed)
- `git` (used to derive image tags)
- For local: [k3d](https://k3d.io/), plus `kubectl` and `helm` installed
  locally (commands run directly against the k3d context)
- For cloud: `ssh` access to a VM with a user that can passwordless-`sudo`
  (needed by `setup-cloud` to install k3s). No local `kubectl`/`helm`
  install needed — those run remotely on the VM over SSH.

All `make` targets take `ENV=local` (default) or `ENV=cloud`. With
`ENV=cloud`, `kubectl`/`helm` run over SSH on the target VM — set
`DEPLOY_USER` and `DEPLOY_HOST` accordingly. Image push/deploy targets
require `GHCR_OWNER` (your GitHub username/org); `GHCR_REPO` defaults to
`convoy`.

By default GHCR packages are assumed public, so no pull credentials are
needed in-cluster. If you make the packages private, create the
`ghcr-pull` imagePullSecret once before bootstrapping/deploying:
```
make ghcr-secret ENV=local GHCR_OWNER=<your-github-user> GHCR_TOKEN=<PAT with read:packages>
```
(add `ENV=cloud DEPLOY_USER=... DEPLOY_HOST=...` for a cloud cluster). Both
app Deployments already reference `imagePullSecrets: [ghcr-pull]`.

## Setup — Local (k3d)

1. Create the local cluster (one-time; not managed by the Makefile since
   it's cluster-level, not app-level):
   ```
   k3d cluster create convoy --servers 1 --agents 0
   ```
2. Build and push both service images (GHCR packages are public, so this
   works the same locally as it does from CI):
   ```
   make image-all push-all GHCR_OWNER=<your-github-user>
   ```
3. Bootstrap the cluster — namespace, Kafka, topic, and the initial
   Deployments/Services for both app services:
   ```
   make bootstrap ENV=local GHCR_OWNER=<your-github-user>
   ```
4. Verify:
   ```
   kubectl get pods -n convoy
   kubectl port-forward -n convoy svc/ingestion-service 8080:8080
   curl localhost:8080/actuator/health
   ```

After this, iterate with `make deploy-service SERVICE=ingestion-service
ENV=local GHCR_OWNER=...` (after `make image push` for that service) to roll
out changes.

## Setup — Cloud VM

1. Provision a VM (any simple VPS) with SSH access and a non-root user that
   can `sudo`. No manual software install needed — `make bootstrap` installs
   k3s and Helm on it for you:
   ```
   make bootstrap ENV=cloud GHCR_OWNER=<your-github-user> \
     DEPLOY_USER=<user> DEPLOY_HOST=<host>
   ```
   This runs `setup-cloud` first (idempotent — safe to re-run; skips
   install if k3s/Helm are already present), then deploys infra + the
   initial app manifests. To only (re-)install k3s/Helm without deploying
   anything:
   ```
   make setup-cloud ENV=cloud DEPLOY_USER=<user> DEPLOY_HOST=<host>
   ```
2. From then on, deploys happen automatically on every push to `main` via
   the GitHub Actions pipeline (see
   [docs/cd-pipeline-spec.md](docs/cd-pipeline-spec.md)). To deploy
   manually instead:
   ```
   make image-all push-all GHCR_OWNER=<your-github-user>
   make deploy-services ENV=cloud GHCR_OWNER=<your-github-user> \
     DEPLOY_USER=<user> DEPLOY_HOST=<host>
   ```

## Teardown

Delete the app services, Kafka, and the namespace:

```
make teardown ENV=local                                    # local
make teardown ENV=cloud DEPLOY_USER=<user> DEPLOY_HOST=<host>  # cloud
```

This removes everything inside the cluster (`convoy` namespace and
all it contains) but leaves the cluster itself running. To also remove the
cluster:

- **Local:** `k3d cluster delete convoy`
- **Cloud:** `ssh <user>@<host> '/usr/local/bin/k3s-uninstall.sh'` (or just
  destroy the VM)

## Rollback

If a deploy misbehaves:

```
make rollback SERVICE=ingestion-service ENV=cloud DEPLOY_USER=<user> DEPLOY_HOST=<host>
```

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
