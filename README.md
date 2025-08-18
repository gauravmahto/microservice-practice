# Stage 7: /run-check endpoint & Kubernetes Job integration with RBAC

Added:
- POST /run-check endpoint that programmatically creates a Kubernetes Job via Fabric8 client
- Fabric8 Kubernetes client dependency (lazy init, disabled via -Dk8s.disabled=true)
- RBAC manifests (ServiceAccount, Role, RoleBinding) granting minimal Job management verbs
- Job manifest template (k8s/job.yaml) for manual runs
- Test verifying 503 behavior when k8s integration disabled

Learning points:
- Programmatic Job creation pattern
- Defensive client initialization & feature toggling
- Least-privilege RBAC for workloads

Next ideas (not implemented):
- Watch Job status endpoint
- Structured logging & metrics
- CI pipeline integration
