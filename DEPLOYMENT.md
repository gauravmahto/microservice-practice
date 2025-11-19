# Deployment Guide

Last Updated: 2025-11-19

## Scope

End-to-end steps to build, load, deploy, verify, upgrade, and remove the Helidon MicroProfile service with its Ansible init container on a local Minikube cluster (Podman/CRIO) using Helm.

## Prerequisites

- JDK 17+
- Docker daemon (for building images) or Podman if adapted
- Minikube running (`minikube start`)
- Helm 3+
- `kubectl` configured for the Minikube context

## 1. Build Application

```bash
./gradlew clean build -x test
```

Artifacts: `build/libs/practice-1.0-SNAPSHOT.jar` (shadow/fat JAR also available if configured).

## 2. Build Images

```bash
docker build --load -t practice-app:1.0.0 .
docker build --load -t practice-ansible-config-runner:1.0.0 ./ansible
```

## 3. Load Images Into Minikube

```bash
minikube image load practice-app:1.0.0
minikube image load practice-ansible-config-runner:1.0.0
```

Images appear inside Minikube with `localhost/` prefix; Helm values should reference `localhost/practice-app` and `localhost/practice-ansible-config-runner`.

## 4. Deploy via Helm

```bash
helm upgrade --install practice-release ./practice-chart
```

Watch progress:

```bash
kubectl get pods -l app.kubernetes.io/name=practice-chart -w
```

## 5. Verify Init Container Success

```bash
POD=$(kubectl get pods -l app.kubernetes.io/name=practice-chart -o jsonpath='$.items[0].metadata.name')
kubectl logs "$POD" -c ansible-config-generator
kubectl exec "$POD" -- ls -la /app/config
kubectl exec "$POD" -- cat /app/config/config.json | jq .
```

## 6. Test Application Endpoints

Port forward or use service URL:

```bash
kubectl port-forward svc/practice-release-practice-chart 8080:8080 &
curl -s localhost:8080/
curl -s localhost:8080/greet
curl -s localhost:8080/config | jq .
curl -s localhost:8080/health
```

## 7. Upgrade Deployment

Change values (e.g., greeting) then:

```bash
helm upgrade practice-release ./practice-chart \
  --set applicationConfig.greeting="Hi from upgrade" \
  --set applicationConfig.features.dashboard=false
```

Check history:

```bash
helm history practice-release
```

## 8. Rollback

```bash
helm rollback practice-release <REVISION>
```

## 9. Scale

```bash
kubectl scale deployment practice-release-practice-chart --replicas=3
kubectl get pods -l app.kubernetes.io/name=practice-chart
```

## 10. Uninstall

```bash
helm uninstall practice-release
```

Confirm cleanup:

```bash
kubectl get all -l app.kubernetes.io/name=practice-chart
```

## 11. Common Failure Modes

- ImageInspectError: Missing Minikube image load; reload images, ensure `pullPolicy: Never`.
- CrashLoopBackOff (init): Template path mismatch; confirm absolute path `/ansible/templates/config.json.j2`.
- Missing config file: Check init logs and volume mount path `/app/config`.

## 12. Automation Script (Optional Idea)

Potential future script `scripts/deploy-local.sh` could chain build → image load → helm upgrade; not yet implemented.

---
See `ARCHITECTURE.md` for deeper component context and `OPERATIONS.md` for ongoing runtime management.
