# Troubleshooting Guide

Common issues and quick diagnostics for the Practice Helidon MicroProfile app.

## Init Container Failures (CrashLoopBackOff)

Symptoms: Pod stuck with init container failing.

Checks:

```bash
POD=$(kubectl get pods -l app.kubernetes.io/name=practice-chart -o jsonpath='{.items[0].metadata.name}')
kubectl logs "$POD" -c ansible-config-generator --tail=200
kubectl describe pod "$POD" | sed -n '1,200p'
```

Common causes:

- Template path mismatch. The playbook must reference `/ansible/templates/config.json.j2`.
- Missing env vars. Ensure `applicationConfig` is set in `values.yaml`.
- File system permission issues writing `/app/config`.

## Image Pull / Inspect Errors

Symptoms: `ImagePullBackOff`, `ErrImagePull`, or `ImageInspectError`.

Checks:

```bash
minikube ssh "sudo crictl images | grep practice"
```

Fixes:

- For Minikube local testing, load images and use `localhost/` prefix with `pullPolicy: Never`.

```bash
minikube image load practice-app:1.0.0
minikube image load practice-ansible-config-runner:1.0.0
```

## Service Not Reachable from Host

Symptoms: `curl: (7) Failed to connect to localhost port 8080`.

Cause: Service is `ClusterIP` (no node port). Use port-forward.

```bash
kubectl port-forward svc/practice-release-practice-chart 8080:8080
curl -sS http://localhost:8080/health/ready
```

## Health Endpoint Down

Symptoms: `/health/ready` or `/health/live` returns non-200 status.

Checks:

```bash
kubectl logs "$POD" -c practice-chart --tail=200
kubectl describe pod "$POD" | grep -A2 -E 'Liveness|Readiness'
```

Fixes:

- Verify application started on the expected port (default 8080).
- Ensure CPU/memory limits are not too restrictive for startup.

## Config Not Updating After Values Change

Symptoms: `/config` returns old values.

Cause: Config is generated at pod startup by the init container.

Fix:

```bash
helm upgrade practice-release ./practice-chart --set applicationConfig.greeting="New message"
kubectl rollout status deploy/practice-release-practice-chart
```

## /run-check Fails (RBAC or Client)

Symptoms: 500 error when hitting POST `/run-check`.

Checks:

- Ensure the pod runs with a ServiceAccount with permissions to create Jobs.
- Apply `k8s/rbac.yaml` and configure the Deployment to use that ServiceAccount.

## General Diagnostics

```bash
# Events and detailed pod info
kubectl describe pod "$POD"
kubectl get events --sort-by='.lastTimestamp' | tail -n 50

# Inspect generated config in the container
kubectl exec "$POD" -- cat /app/config/config.json | jq .

# Render Helm templates and lint
helm lint ./practice-chart
helm template test-release ./practice-chart | sed -n '1,200p'
```

If issues persist, capture `kubectl describe pod` output and container logs from both the init and main containers when filing a report.
