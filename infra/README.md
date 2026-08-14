# Infra

Non-application infrastructure components, one subdirectory per component:

- `k8s/` — cluster-wide resources shared across all infra (currently just
  the `fleet-ingestion` namespace).
- `kafka/` — Kafka Helm values + topic definitions.

Future components (database, etc.) follow the same pattern: a new
`infra/<component>/` directory with its own Helm values / manifests, wired
into the Makefile's `deploy-infra` target alongside `kafka`.
