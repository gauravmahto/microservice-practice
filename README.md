# Practice Helidon MicroProfile App

Lightweight Helidon MicroProfile service demonstrating:

* Greeting endpoint `/greet` (config-driven message via MicroProfile Config)
* Config dump `/config` (filtered `server.*` + `app.*` keys)
* Health endpoints `/health`, `/health/live`, `/health/ready`
  * Liveness: built-in checks (heap, deadlock, etc.)
  * Readiness: application readiness flag
* Automatic port fallback: if configured port is busy it retries with an ephemeral port
* Kubernetes probes aligned with health endpoints
* Built with Gradle + Shadow (fat) JAR
* Optional Kubernetes Job trigger endpoint: POST `/run-check` (creates a one-off Job when a Kubernetes client is available)

## Source highlights

* JAX-RS registration: `src/main/java/com/example/RestApplication.java`
* Greeting endpoint: `src/main/java/com/example/GreetingResource.java`
* Config dump endpoint: `src/main/java/com/example/ConfigResource.java`
* Job trigger: `src/main/java/com/example/RunCheckResource.java` (POST `/run-check`, JSON payload)
* Kubernetes client wiring: `src/main/java/com/example/KubernetesClientProducer.java`
* Build script: `build.gradle`
* Simple readiness Job used by `/run-check`: `simple-check/` (Python script and Dockerfile)

## Requirements

* JDK 17+
* Gradle (wrapper included)
* Docker (for container build)
* kubectl + a Kubernetes cluster (for `/run-check` integration)

## Build & test (local)

Use the Gradle wrapper included in the repository:

```bash
./gradlew clean build
./gradlew test

# Run app (Gradle)
./gradlew run
# Run app (Shadow via Gradle)
./gradlew runShadow

# Or run the fat JAR created by the shadow plugin
java -jar build/libs/practice-1.0-SNAPSHOT-all.jar

# Alternative: create an installable distribution and run the generated script
## This produces a platform-specific launch script under `build/install/<project>/bin`
./gradlew clean installDist  

./build/install/practice/bin/practice
```

## Notes

* Why avoid `./gradlew run` for CDI apps: the `run` task executes the application on Gradle's runtime classpath which can change service-loading order and classpath visibility; that can occasionally break CDI/service discovery.

* Safer alternatives:

  * `./gradlew runShadow` — runs the app using the shadow classpath (closer to the packaged runtime).

  * `java -jar build/libs/practice-1.0-SNAPSHOT-all.jar` — run the fat JAR produced by the Shadow plugin.

  * `./gradlew clean installDist` then `./build/install/practice/bin/practice` — produces a distribution with a launcher script that runs with a stable classpath.

## Configuration

This project uses MicroProfile config. Edit `src/main/resources/microprofile-config.properties` (or pass `-D` system properties / env vars).

Example properties (the project may also include `microprofile-config.properties` in `src/main/resources`):

```properties
server.port=8080
app.greeting=Hello from config!
```

## Quick smoke tests

After starting (default port 8080):

```bash
curl -s localhost:8080/
curl -s localhost:8080/config
curl -s localhost:8080/health
curl -s localhost:8080/health/live
curl -s localhost:8080/health/ready
curl -s -X POST localhost:8080/run-check -H 'Content-Type: application/json' -d '{}'
# The /run-check endpoint accepts a JSON body with overrides; see "RunCheck" section below
```

## Docker

```bash
# Build image (uses multi-stage Dockerfile)
docker build -t practice-app:1.0.0 .

# Run container
docker run --rm -p 8080:8080 --name practice practice-app:1.0.0

# Smoke test
curl -s localhost:8080/health
```

## Kubernetes

Apply manifests (adjust image tag to one you built/pushed). If you plan to use the `/run-check` endpoint or run Jobs programmatically, apply RBAC first (creates the ServiceAccount referenced by the Deployment):

```bash
# (Optional) load image into local cluster (example for kind)
# kind load docker-image practice-app:1.0.0

# (1) RBAC (ServiceAccount, Role, RoleBinding) – needed for /run-check feature
kubectl apply -f k8s/rbac.yaml

# (2) Core workload + networking
kubectl apply -f k8s/deployment.yaml -f k8s/service.yaml -f k8s/ingress.yaml

# (3) Verify
kubectl get pods -l app=practice-app
kubectl get svc practice-service
```

Port-forward (if no ingress controller):

```bash
kubectl port-forward svc/practice-service 8080:80
curl -s localhost:8080/health/ready
```

## Flux / GitOps (Flux bootstrap & deploy)

This repo is set up for Flux-based GitOps. Below are the exact commands and checks used when bootstrapping Flux to this repository and deploying the `practice-chart` via a `HelmRelease` stored under `clusters/practice`.

Prerequisites

* `flux` CLI installed and `kubectl` pointed at the target cluster.
* GitHub credentials via `gh auth login` or `GITHUB_TOKEN` with repo + admin:repo_hook scopes.

Bootstrap Flux (example filled for this repo)

```bash
flux bootstrap github \
  --owner=gauravmahto \
  --repository=microservice-practice \
  --branch=micro-profile \
  --path=./clusters/practice \
  --personal
```

Add a HelmRelease into the repo path Flux watches

1. Place your `HelmRelease` under `clusters/practice/releases/` (example file: `clusters/practice/releases/practice-app.yaml`).
2. Commit & push on the branch Flux is watching (in this repo we used `micro-profile`):

```bash
git add clusters/practice/releases/practice-app.yaml
git commit -m "Add practice-app HelmRelease under clusters/practice for Flux"
git push origin micro-profile
```

Trigger reconciliation (optional; Flux will pick up the commit automatically)

```bash
flux reconcile source git flux-system -n flux-system
flux reconcile kustomization flux-system -n flux-system
```

Verify the deployment

```bash
flux get sources git -n flux-system
flux get kustomizations -n flux-system
flux get helmreleases -n flux-system
kubectl get all -n practice
kubectl get pods -n practice
kubectl get helmrelease practice-app -n flux-system -o yaml
```

Quick debug & logs

* If `flux get helmreleases` returns nothing: ensure the `HelmRelease` file is committed under the repo path (e.g. `clusters/practice/releases`) that Flux bootstrapped to.
* Check the GitRepository status used by Flux:

```bash
kubectl get gitrepository flux-system -n flux-system -o yaml
```

* Tail pod logs in the `practice` namespace:

```bash
kubectl logs -n practice -l app=practice-app --follow
```

* Inspect Flux controllers and events:

```bash
kubectl get pods -n flux-system
kubectl logs -n flux-system -l app=source-controller --tail=200
kubectl get events -n flux-system --sort-by='.metadata.creationTimestamp'
```

Notes

* In this repo we moved `releases/practice-app.yaml` -> `clusters/practice/releases/practice-app.yaml` so Flux would apply it; committing and pushing that change triggered Flux to install the HelmRelease into cluster and the HelmRelease created resources in namespace `practice`.
* Keep your Git branch and `--path` aligned: Flux will commit and sync to the specific branch and path you specify when bootstrapping.


### Kubernetes health probes

`k8s/deployment.yaml` uses the Helidon endpoints:

```yaml
livenessProbe:
  httpGet:
    path: /health/live
    port: http
  initialDelaySeconds: 20
  periodSeconds: 15
  timeoutSeconds: 2
  failureThreshold: 3
readinessProbe:
  httpGet:
    path: /health/ready
    port: http
  initialDelaySeconds: 5
  periodSeconds: 10
  timeoutSeconds: 2
  failureThreshold: 3
```

## Shadow JAR vs standard JAR

* Standard: `build/libs/practice-1.0-SNAPSHOT.jar`
* Fat (all deps): `build/libs/practice-1.0-SNAPSHOT-all.jar` (preferred for `java -jar` / Docker)

## Simple check (readiness Job)

See `simple-check/` — a small Python script (`check.py`) and Dockerfile used by the Kubernetes Job manifest `k8s/job.yaml`.

Run locally against a running server:

```bash
python3 simple-check/check.py --url http://localhost:8080/health/ready --timeout 10
```

## RunCheck endpoint (POST /run-check)

The `/run-check` endpoint creates a Kubernetes `Job` programmatically. The handler accepts a JSON object (request body) that can override the most common Job fields. If the cluster client is not available or RBAC is missing, the create will fail.

Supported request JSON fields (all optional):

* `namespace` (string) – target namespace (defaults to the producer's default namespace)
* `jobName` (string) – desired job name; if it already exists a short suffix is appended
* `image` (string) – container image (default: `busybox:1.36`)
* `command` (array of strings) – container entrypoint (default: `["sh","-c"]`)
* `args` (array of strings) – container args (default: `["echo 'RunCheck OK' && sleep 3"]`)
* `env` (object) – map of env var name -> value injected into the container
* `labels` (object) – labels for job/pod (defaults: `app: run-check, managed-by: helidon-mp`)
* `backoffLimit` (int) – Job `backoffLimit` (default: 0)
* `ttlSecondsAfterFinished` (int) – TTL for finished Job (default: 60)
* `activeDeadlineSeconds` (int) – optional active deadline (seconds)
* `serviceAccountName` (string) – pod serviceAccountName (optional)
* `restartPolicy` (string) – pod restart policy (default: `Never`)
* `parallelism` / `completions` (ints) – defaults to 1

Example minimal call:

```bash
curl -X POST localhost:8080/run-check \
  -H 'Content-Type: application/json' \
  -d '{"image":"busybox:1.36","args":["echo hello && sleep 2"]}'
```

Behavior and responses:

* 200 OK with JSON `RunCheckResponse` on success (includes created Job name/uid)
* If the requested `jobName` already exists a unique suffix is appended and returned
* 500 on exception during Job creation (response will contain an error message)

Note: the endpoint requires the Fabric8 `KubernetesClient` to be available via CDI injection and appropriate RBAC if running in-cluster.

## Build / dependency notes

The `build.gradle` now declares Helidon MicroProfile (Helidon 3.x), and includes the Fabric8 Kubernetes client (`io.fabric8:kubernetes-client`) and BouncyCastle libraries used by utility code. The project builds a fat JAR via the Shadow plugin.

## Troubleshooting

| Problem | Fix |
|---|---|
| Port busy on 8080 | `lsof -nP -iTCP:8080 -sTCP:LISTEN` then `kill <pid>`; app auto-retries ephemeral port if busy |
| `/run-check` fails with permission or client errors | Ensure `k8s/rbac.yaml` is applied, the Deployment uses the ServiceAccount, and the cluster can pull the job image |
| Job pods `ImagePullBackOff` | Ensure the image is present in the cluster or pushed to a registry reachable by the cluster |

## Cleanup

```bash
docker rm -f practice 2>/dev/null || true
kubectl delete -f k8s/ingress.yaml -f k8s/service.yaml -f k8s/deployment.yaml 2>/dev/null || true
```

## License

Internal practice project
See `simple-check/` — a small Python script (`check.py`) and Dockerfile used by the Kubernetes Job manifest `k8s/job.yaml`.
