# Deployment (Cloud VM)

Information needed before Convoy can be deployed to a cloud VM, per
[docs/cd-pipeline-spec.md](docs/cd-pipeline-spec.md). See
[docs/implementation-spec.md](docs/implementation-spec.md) §9 for how the VM
spec below was sized.

## VM access

- VM address/IP (`DEPLOY_HOST`)
- SSH user with passwordless `sudo` (`DEPLOY_USER`)
- SSH private key for that user (`DEPLOY_SSH_KEY`)

## VM state

- Fresh VM (nothing installed) or does it already have k3s running?
- Spec should match **4 vCPU / 8GB RAM** (see cd-pipeline-spec.md §2 for
  common cloud matches). If the VM is smaller, the resource limits in
  `infra/kafka/values.yaml` and `infra/observability/values.yaml` need
  retuning first.

## GHCR

- GitHub username/org for image tags (`GHCR_OWNER`) —
  `ghcr.io/<owner>/convoy/...`
- Whether GHCR packages are public (no pull secret needed) or private
  (needs `make ghcr-secret` with a PAT)

## Scope

- One-time bootstrap only (`make bootstrap ENV=cloud ...` +
  `make deploy-init ENV=cloud ...`, run by hand), or
- Also wiring up the GitHub Actions CD pipeline — add `DEPLOY_SSH_KEY`,
  `DEPLOY_HOST`, `DEPLOY_USER` as repository secrets so pushes to `main`
  auto-deploy (see docs/cd-pipeline-spec.md §5)

## Accessing Grafana on the cloud VM

Grafana is deployed as a `ClusterIP` service (`kube-prometheus-stack-grafana`,
port 80 in the `observability` namespace) — not exposed to the internet, by
design. Reach it via an SSH tunnel. Local port `3001` is used below to avoid
colliding with a local (`ENV=local`) Grafana instance, which would also be on
`3000`:

```
ssh -i <path-to-DEPLOY_SSH_KEY> -L 3001:localhost:3001 <DEPLOY_USER>@<DEPLOY_HOST> \
  'KUBECONFIG=/etc/rancher/k3s/k3s.yaml kubectl port-forward -n observability svc/kube-prometheus-stack-grafana 3001:80'
```

Leave that running, then open http://localhost:3001 in a browser.

**Credentials:** username is always `admin`. Get the password by running,
on the VM (over the same SSH connection, or via `ssh <DEPLOY_USER>@<DEPLOY_HOST> '...'`):

```
KUBECONFIG=/etc/rancher/k3s/k3s.yaml kubectl get secret -n observability \
  kube-prometheus-stack-grafana -o jsonpath='{.data.admin-password}' | base64 -d; echo
```

Not documented here since it's generated per-deployment (random, set by the
Helm chart at install time) — always fetch it fresh rather than hardcoding
it anywhere.
