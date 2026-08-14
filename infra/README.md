# Infra

Non-application infrastructure components, one subdirectory per component:

- `k8s/` — cluster-wide resources shared across all infra (currently just
  the `convoy` namespace).
- `kafka/` — Kafka Helm values + topic definitions.
- `observability/` — kube-prometheus-stack (Prometheus, Grafana,
  Alertmanager) Helm values + PodMonitors, deployed into its own
  `observability` namespace so it can be torn down independently of the
  `convoy` app/infra stack. Wired into the Makefile's `deploy-observability`
  target, separate from `deploy-infra`. Includes a pre-built Grafana
  dashboard (`grafana-dashboards.yaml`) covering per-service container
  CPU/memory, JVM heap/non-heap/thread metrics, and G1 GC pause/allocation/
  promotion metrics — auto-loaded by Grafana's dashboard sidecar, no manual
  import needed.

Future components (database, etc.) follow the same pattern: a new
`infra/<component>/` directory with its own Helm values / manifests, wired
into the Makefile alongside `kafka` / `observability`.
