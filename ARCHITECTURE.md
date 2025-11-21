# Architecture & Operational Memory (Continuous Update)

Last Updated: 2025-11-19

## 1. Overview

This project is a Helidon MicroProfile Java microservice packaged via Gradle and deployed to Kubernetes using a Helm chart. Runtime configuration is dynamically generated at pod startup by an Ansible init container which renders a JSON config file consumed by the main application.

## 2. Components

- **Helidon MicroProfile App**: Provides REST endpoints (`/`, `/greet`, `/config`, `/run-check`) plus standard health endpoints under `/health`.
- **Init Container (Ansible)**: Image built from `ansible/Dockerfile`, runs playbook `ansible/playbooks/generate_config.yml` to create `/app/config/config.json` using template `ansible/templates/config.json.j2` and environment variables.
- **Shared Volume**: `emptyDir` volume (`config-volume`) mounted at `/app/config` in both init and main containers.
- **Helm Chart**: Located in `practice-chart/`. Template `deployment.yaml` defines initContainer, volume, env vars, and merges user volume mounts.
- **Kubernetes Objects**: Deployment (replicas configurable), Service (ClusterIP), optional Ingress & HPA (templates present), ServiceAccount.
- **Local Observability Stack**: Ansible playbook (`observability.yml`) that provisions Prometheus and Grafana containers locally for monitoring the application during development.

## 3. Build & Images

- **Build Command**: `./gradlew build` produces app JAR under `build/libs/`.
- **App Image**: Multi-stage Dockerfile (root `Dockerfile`) builds and packages Helidon app.
- **Init Image**: `ansible/Dockerfile` based on `cytopia/ansible:2.10`, copies playbook + templates.
- **Local Minikube Usage**: Images loaded via `minikube image load`; chart values use `localhost/<image>` and `imagePullPolicy: Never` to avoid external pulls.

## 4. Deployment Flow

1. Helm install/upgrade: `helm install practice-release ./practice-chart` or `helm upgrade practice-release ./practice-chart`.
2. Pod starts; init container runs Ansible playbook.
3. Playbook ensures `/app/config` exists and renders `config.json` from template and env vars.
4. Main container starts, reads config at `/app/config/config.json`.
5. Service exposes port `8080` internally; optionally port-forward or use `minikube service` for external access.

## 5. Dynamic Config Generation

- **Env Vars (values.yaml)**:
  - `APP_GREETING` -> `greetingMessage`
  - `FEATURE_FLAG_DASHBOARD`, `FEATURE_FLAG_APIV2` -> `featureFlags` map
- **Template**: `config.json.j2` references Jinja lookups of these env vars to produce JSON.
- **Result File**: `/app/config/config.json` consumed by application endpoint `/config`.

## 6. Helm Chart Key Sections

- `values.yaml` keys:
  - `image` (app) and `configGenerator` (init container) repo/tag/pullPolicy.
  - `applicationConfig` block holds greeting + feature flags (source for env vars → Ansible template).
  - `service`, `ingress`, `autoscaling` (HPA), resource stubs.
- `templates/deployment.yaml`:
  - Declares `volumes:` with `emptyDir` `config-volume`.
  - `initContainers:` block: name `ansible-config-generator`, mounts `/app/config`.
  - Merges user-defined `volumeMounts` while ensuring `config-volume` present.
  - Sets env vars from `applicationConfig`.

## 7. Endpoints

| Path | Purpose |
|------|---------|
| `/` | Root basic response |
| `/greet` | Greeting endpoint (may use greeting message) |
| `/config` | Returns rendered dynamic config JSON |
| `/run-check` | Custom internal or diagnostic action |
| `/health` | Health group (readiness/liveness) |

## 8. Troubleshooting Notes (Historical)

- **ImageInspectError**: Resolved by prefixing image repo with `localhost/` and setting pullPolicy `Never` for Minikube local images.
- **CrashLoopBackOff (init)**: Root cause was incorrect relative template path; fixed by using absolute `/ansible/templates/config.json.j2` in playbook.
- **Non-existent Endpoint**: `/hello` does not exist; documentation corrected to real endpoints.

## 9. Maintenance Checklist

Perform these whenever related changes occur:

1. Modify Init Container logic → Update section 5 (Dynamic Config) and 2 (Components).
2. Add/remove env vars → Reflect in `values.yaml` summary and template mapping table.
3. Change volume name/path → Update sections 2, 4, 5, and deployment description.
4. Add endpoints → Extend table in section 7.
5. Adjust image naming strategy (e.g., moving to registry) → Update section 3 and troubleshooting.
6. Enable HPA or Ingress → Document activation and required values under section 6.
7. Security/resource limits added → Note under Components or a new Security subsection.

## 10. Suggested Future Enhancements

- Toggle flag in `values.yaml` to disable init container when static config is sufficient.
- Script to diff `config.json` against `values.yaml` for consistency.
- Resource limits & securityContext hardening.
- Automated test verifying `/config` matches expected JSON from provided Helm values.

## 11. Update Procedure

1. Make code/chart change.
2. Regenerate images if necessary and deploy.
3. Verify behavior (`kubectl exec ... cat /app/config/config.json`).
4. Update this file (increment Last Updated date).
5. (Optional) Commit: `git add ARCHITECTURE.md && git commit -m "docs: update architecture memory"`.

## 12. Reference Commands

```bash
# Build & load images
./gradlew build
docker build -t localhost/practice-app:latest .
docker build -t localhost/practice-config-generator:latest ./ansible
minikube image load localhost/practice-app:latest
minikube image load localhost/practice-config-generator:latest

# Deploy / upgrade
helm upgrade --install practice-release ./practice-chart

# Inspect config generated by init container
POD=$(kubectl get pods -l app.kubernetes.io/name=practice-chart -o jsonpath='$.items[0].metadata.name')
kubectl exec "$POD" -- cat /app/config/config.json | jq .
```

---
If any section becomes stale, update promptly to honor the "always keep it up to date" directive.
