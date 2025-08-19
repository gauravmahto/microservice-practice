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

# Or run the fat JAR created by the shadow plugin
java -jar build/libs/practice-1.0-SNAPSHOT-all.jar
```

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
