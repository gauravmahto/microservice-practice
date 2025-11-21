# Operations Guide

Day-2 operational steps and commands for running the Practice Helidon MicroProfile app in Kubernetes.

## Quick Health & Status

```bash
# Pod and deployment state
kubectl get deploy,rs,pod -l app.kubernetes.io/name=practice-chart

# Health endpoints via port-forward (ClusterIP service)
kubectl port-forward svc/practice-release-practice-chart 8080:8080
curl -sS http://localhost:8080/health/ready
curl -sS http://localhost:8080/health/live
```

## Logs & Exec

```bash
# App container logs
POD=$(kubectl get pods -l app.kubernetes.io/name=practice-chart -o jsonpath='{.items[0].metadata.name}')
kubectl logs "$POD" -c practice-chart --tail=200 -f

# Init container logs (Ansible run)
kubectl logs "$POD" -c ansible-config-generator --tail=200

# Exec into pod
kubectl exec -it "$POD" -c practice-chart -- sh
```

## Configuration Lifecycle

The application reads `/app/config/config.json`, generated at startup by the init container from Helm values. To change runtime config:

```bash
# Update values and roll the deployment
helm upgrade practice-release ./practice-chart \
  --set applicationConfig.greeting="Hello operators!" \
  --set applicationConfig.features.dashboard=true

# Verify the generated file after pods restart
POD=$(kubectl get pods -l app.kubernetes.io/name=practice-chart -o jsonpath='{.items[0].metadata.name}')
kubectl exec "$POD" -- cat /app/config/config.json | jq .
curl -sS http://localhost:8080/config | jq .
```

Notes:

- Config is rendered only at pod start. Changing values triggers a rolling restart (via Helm upgrade) to regenerate.
- The shared `emptyDir` volume lives for the pod lifetime only.

## Local Observability Stack

You can spin up a local Prometheus and Grafana stack using Ansible and Podman to monitor the application (or learn by example).

### Prerequisites

- Ansible
- Podman (and `containers.podman` collection)

### Start the Stack

```bash
ansible-playbook ansible/playbooks/observability.yml
```

### Access Dashboards

- **Prometheus**: [http://localhost:9090](http://localhost:9090)
- **Grafana**: [http://localhost:3000](http://localhost:3000)
  - **User**: `admin`
  - **Password**: `admin`

### Configuration

- Config files are generated in `ansible/generated_config/`.
- Prometheus scrapes `host.docker.internal:8080` by default. Ensure your app is running locally on port 8080.

### Stop/Cleanup

```bash
ansible-playbook ansible/playbooks/teardown.yml
```

## Scaling & Rollouts

```bash
# Scale replicas
kubectl scale deploy practice-release-practice-chart --replicas=3

# Trigger a rolling restart (e.g., after a ConfigMap/env change)
kubectl rollout restart deploy/practice-release-practice-chart
kubectl rollout status deploy/practice-release-practice-chart
```

## Service Access

The Service is `ClusterIP` by default. Use port-forward for local access:

```bash
kubectl port-forward svc/practice-release-practice-chart 8080:8080
curl -sS http://localhost:8080/
```

To expose outside the cluster, enable an Ingress controller and set values for the chart’s Ingress template, or change Service type to `NodePort` (for dev only).

## On-Call Runbook Snippets

```bash
# Describe pod for events
kubectl describe pod "$POD"

# Check recent namespace events
kubectl get events --sort-by='.lastTimestamp' | tail -n 50

# Top pods
kubectl top pod -l app.kubernetes.io/name=practice-chart

# Validate endpoints
curl -sS http://localhost:8080/health/ready | jq .
curl -sS http://localhost:8080/greet
```

## Decommission

See `DECOMMISSION.md` for safe teardown and image cleanup steps.
