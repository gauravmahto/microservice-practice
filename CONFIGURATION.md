# Configuration Guide

Last Updated: 2025-11-19

## Purpose

Explain how runtime configuration is produced (init container → generated JSON) and how you customize behavior via Helm values and MicroProfile config.

## Layers

1. Helm values (`practice-chart/values.yaml`) → environment variables for init container.
2. Init container (Ansible) renders `/app/config/config.json` using Jinja template.
3. Main app reads config file and MicroProfile config sources (file, env vars, system properties).

## Helm Values Keys (Excerpt)

```yaml
applicationConfig:
  greeting: "Hello from Helm!"
  features:
    dashboard: true
    apiV2: false
configGenerator:
  image:
    repository: localhost/practice-ansible-config-runner
    tag: "1.0.0"
    pullPolicy: Never   # configurable; use Never for Minikube local images
```

Env Vars produced:

| Helm Key | Env Var | Template Mapping |
|----------|---------|------------------|
| applicationConfig.greeting | APP_GREETING | greetingMessage |
| applicationConfig.features.dashboard | FEATURE_FLAG_DASHBOARD | featureFlags.enableNewDashboard |
| applicationConfig.features.apiV2 | FEATURE_FLAG_APIV2 | featureFlags.enableApiV2 |

## Generated JSON Structure (`/app/config/config.json`)

```json
{
  "greetingMessage": "Hello from Helm!",
  "featureFlags": {
    "enableNewDashboard": true,
    "enableApiV2": false
  }
}
```

## Overriding at Install Time

```bash
helm upgrade --install practice-release ./practice-chart \
  --set applicationConfig.greeting="Custom Greeting" \
  --set applicationConfig.features.dashboard=false \
  --set applicationConfig.features.apiV2=true
```

### Init Image Pull Policy

To change how the init image is pulled (useful when pushing to a registry instead of Minikube local images):

```bash
helm upgrade --install practice-release ./practice-chart \
  --set configGenerator.image.pullPolicy=IfNotPresent
```

Defaults to `Never` for local Minikube flows.

## Post-Deployment Changes

Helm upgrade regenerates config only on new pods (recreate or rolling update). To force regeneration:

```bash
helm upgrade practice-release ./practice-chart --reuse-values
kubectl rollout restart deployment practice-release-practice-chart
```

## Adding New Feature Flags

1. Add new key under `applicationConfig.features` in `values.yaml`.
2. Pass it as an env var in deployment template (if not auto-rendered).
3. Update `ansible/templates/config.json.j2` to include mapping.
4. Update docs (`CONFIGURATION.md`, `ARCHITECTURE.md`).

## Additional Volumes

You can append extra volumes via `values.yaml`:

```yaml
volumes:
  - name: extra-config
    configMap:
      name: my-config
```

These are merged into the same `volumes` list; the built-in `config-volume` remains present.

## MicroProfile Overrides

You can still override MicroProfile config independently:

```bash
kubectl set env deployment/practice-release-practice-chart APP_GREETING="Override via env"
```

If override diverges from generated file, document intent; prefer Helm-managed approach for consistency.

---
For operational commands refer to `OPERATIONS.md`; architectural context in `ARCHITECTURE.md`.
