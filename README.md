# Practice Helidon SE App

Lightweight Helidon SE microservice demonstrating:

* Greeting endpoint `/` (config-driven message)
* Config dump `/config` (filtered `server.*` + `app.*` keys)
* Health endpoints `/health`, `/health/live`, `/health/ready`
  * Liveness: built-in `heapMemory` & `deadlock` checks
  * Readiness: custom flag set only after full server start
* Automatic port fallback: if configured port is busy it retries with an ephemeral port
* Kubernetes probes aligned with health endpoints
* Built with Gradle + Shadow (fat) JAR
* Optional Kubernetes Job trigger endpoint: POST `/run-check` (creates a one-off Job when K8s client enabled)

## Source Highlights

* Main app: [`SimpleWebServer.java`](src/main/java/com/example/SimpleWebServer.java)
* Config: [`application.yaml`](src/main/resources/application.yaml)
* Build script: [`build.gradle`](build.gradle)
* Test: [`SimpleWebServerTest.java`](src/test/java/com/example/SimpleWebServerTest.java)
* Container: [`Dockerfile`](Dockerfile)
* K8s manifests: [`deployment.yaml`](k8s/deployment.yaml), [`service.yaml`](k8s/service.yaml), [`ingress.yaml`](k8s/ingress.yaml), [`rbac.yaml`](k8s/rbac.yaml), [`job.yaml`](k8s/job.yaml)

## Requirements

* JDK 17+
* Gradle (wrapper or local)
* Docker (for container build)
* kubectl + a Kubernetes cluster

## Build & Test (Local)

```bash
# Clean & compile + run tests
gradle clean build

# Run only tests
gradle test

# Run app (Gradle)
gradle run

# Or run fat jar (created by shadow plugin)
java -jar build/libs/practice-1.0-SNAPSHOT-all.jar
```

## Configuration

Edit [`application.yaml`](src/main/resources/application.yaml):

```yaml
server.port: 8080
app.greeting: "Hello from config!"
```

## Local Smoke Tests

After starting (port 8080):

```bash
curl -s localhost:8080/
curl -s localhost:8080/config
curl -s localhost:8080/health
curl -s localhost:8080/health/live
curl -s localhost:8080/health/ready
curl -s -X POST localhost:8080/run-check   # may return 503 if k8s disabled or client not configured
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

Apply manifests (adjust image tag to one you built/pushed). If you plan to use the `/run-check` endpoint or run Jobs programmatically, apply RBAC first (creates ServiceAccount referenced by the Deployment):

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

Ingress (DNS or /etc/hosts must map practice.local to ingress IP):

```bash
curl -H "Host: practice.local" http://<ingress-ip>/health
```

### Kubernetes Health Probes

`deployment.yaml` uses the dedicated Helidon endpoints:

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

Optional startup probe (add if cold start slows):

```yaml
startupProbe:
 httpGet:
  path: /health/live
  port: http
 initialDelaySeconds: 5
 periodSeconds: 5
 failureThreshold: 12  # ~60s max startup
```

Apply changes:

```bash
kubectl apply -f k8s/deployment.yaml
```

## Running Tests as Smoke Check

```bash
gradle test
# See HTML report:
open build/reports/tests/test/index.html
```

## Shadow JAR vs Standard JAR

* Standard: build/libs/practice-1.0-SNAPSHOT.jar
* Fat (all deps): build/libs/practice-1.0-SNAPSHOT-all.jar (preferred for java -jar / Docker)

## Cleanup

```bash
docker rm -f practice 2>/dev/null || true
kubectl delete -f k8s/ingress.yaml -f k8s/service.yaml -f k8s/deployment.yaml 2>/dev/null || true
```

## Source Code Compression Utility

Create a clean source archive excluding ignored files (`build/`, `.gradle/`, etc.). Two options:

1. Git-based Gradle task (tracked files only):

```bash
gradle gitSourceArchive
ls -lh build/distributions/*source*.tar.gz
```

1. Script with optional inclusion of untracked (non-ignored) files:

```bash
chmod +x scripts/compress-source.sh   # first time
scripts/compress-source.sh                # tracked files only
scripts/compress-source.sh --include-untracked  # add new files you haven't committed
scripts/compress-source.sh --output /tmp/src.tar.gz
```

Both methods respect `.gitignore` (ignored files are not packaged). The archive name embeds the current short commit hash.

## Simple Check Batch Job

This repo includes a deterministic readiness verification batch Job under `simple-check/` and a Kubernetes `Job` manifest at `k8s/job.yaml`.

### Readiness Check Script (`simple-check/check.py`)

The Python script performs an HTTP GET against a readiness endpoint (defaults to the in-cluster service `http://practice-service/health/ready`). It succeeds only if:

* HTTP status is 200
* JSON body has overall `{"status":"UP"}`

Configuration precedence (first present wins):

1. CLI argument `--url`
2. Environment variable `TARGET_URL`
3. Built from `TARGET_HOST` + `TARGET_PORT` + `TARGET_PATH`
4. Fallback default: `http://practice-service/health/ready`

Other env / args:

* `--timeout` argument or `TOTAL_TIMEOUT` env (seconds, default 30)

Exit codes:

* `0` success (ready)
* `1` failure (not ready within timeout / invalid response)

Run locally (after starting the Java server):

```bash
python3 simple-check/check.py --url http://localhost:8080/health/ready --timeout 10
```

Simulate failure:

```bash
python3 simple-check/check.py --url http://localhost:65530/health/ready --timeout 5 || echo "failed as expected"
```

### Build the Job Image

```bash
cd simple-check
docker build -t simple-check-app .
cd -
```

### Run the Job in Kubernetes (manual manifest)

Ensure the image `simple-check-app` is available to your cluster (loaded into kind / minikube cache or pushed to a registry the cluster can pull from). Then apply:

```bash
kubectl apply -f k8s/job.yaml
```

`job.yaml` uses `generateName:` so you can apply it repeatedly without editing; Kubernetes appends a unique suffix.

The manifest sets `imagePullPolicy: IfNotPresent` so a locally-built image is reused (works for kind/minikube if loaded).

Check status & logs:

```bash
kubectl get jobs
kubectl get pods -l job-name=simple-check-job-1
kubectl logs -l job-name=simple-check-job-1 --tail=100
```

Get last Job name quickly:

```bash
JOB=$(kubectl get jobs -o jsonpath='{.items[-1:].0.metadata.name}')
echo $JOB
```

Tail pod logs (auto-detect):

```bash
kubectl logs -l job-name=$JOB --tail=100
```

Watch Job until completion:

```bash
kubectl get job $JOB -w
```

Delete the Job (and its pod):

```bash
kubectl delete job simple-check-job-1
```

Adjust `backoffLimit` if you want automatic retries (currently `0` => no retries). Update `name` each re-run to avoid "already exists" errors.

## Kubernetes Job Trigger Endpoint (`/run-check`)

The application exposes a POST endpoint `/run-check` that programmatically creates a Kubernetes Job similar to `k8s/job.yaml` but with a dynamically generated name (timestamp) and environment variables (`TOTAL_TIMEOUT=30`, optional `TARGET_URL`).

```bash
curl -X POST http://localhost:8080/run-check
```

### Enabling the Endpoint

The endpoint is available when:

1. The app is running inside (or has access to) a Kubernetes cluster configuration (kubeconfig / in-cluster).
2. The system property **`k8s.disabled` is NOT set to `true`** (tests set it to disable side effects).
3. RBAC resources from `k8s/rbac.yaml` are applied and the Deployment uses the provided ServiceAccount (`practice-app-sa`).
4. A container image for the job (`simple-check-app`) is present in the cluster (build and load/push first – see below).

If any prerequisite is missing the endpoint returns `503`.

Behavior:

* `200 OK` + Job name when client initializes and create succeeds.
* `503` when feature disabled (`k8s.disabled=true`) or client init failed / kube API not reachable.
* `500` on exception during Job creation.

### Advanced Usage

Query parameters:

* `image` – override container image (default `simple-check-app:latest` or `-Dcheck.image=` system property)
* `url` – explicit target URL injected as `TARGET_URL` env var (default built inside script)

System properties (JVM start args):

* `-Dcheck.image=<image>` global default for Job image
* `-Dcheck.target.url=<url>` fallback if no query param provided

Example overrides:

```bash
curl -X POST "http://localhost:8080/run-check?image=simple-check-app:latest&url=http://practice-service/health/ready"
```

### Verify Job After Trigger

```bash
kubectl get jobs | grep simple-check
kubectl logs -l job-name=<returned-job-name>
```

Cleanup old Jobs (keep last 5):

```bash
kubectl get jobs -o name | head -n -5 | xargs -r kubectl delete
```

Disabling (default in tests): set system property `-Dk8s.disabled=true` (already applied for the Gradle `test` task in `build.gradle`).

### Building the Job Image for /run-check

Build the `simple-check` image so dynamically created Jobs can pull it:

```bash
docker build -t simple-check-app ./simple-check
# For kind:
kind load docker-image simple-check-app || true
# For minikube:
minikube image load simple-check-app || true
```

Ensure the image reference inside the handler (`simple-check-app`) matches what you built/pushed or modify both the code and `k8s/job.yaml` accordingly.

The client is lazily initialized; failures are logged once and the endpoint remains in a disabled state until a later attempt succeeds.

### End-to-End Kubernetes Demo (All Steps)

```bash
# 0. Namespace (optional)
kubectl create namespace practice || true
kubectl config set-context --current --namespace=practice

# 1. Build images locally
docker build -t practice-app:1.0.0 .
docker build -t simple-check-app:latest simple-check

# 2. Load images into cluster (choose one)
kind load docker-image practice-app:1.0.0 simple-check-app:latest || true
# or
minikube image load practice-app:1.0.0; minikube image load simple-check-app:latest

# 3. RBAC + core resources
kubectl apply -f k8s/rbac.yaml
kubectl apply -f k8s/deployment.yaml -f k8s/service.yaml

# 4. Wait for pods ready
kubectl rollout status deployment/practice-app

# 5. Trigger a check
kubectl port-forward deploy/practice-app 8080:8080 &
sleep 2
curl -X POST http://localhost:8080/run-check

# 6. Inspect jobs & logs
kubectl get jobs
JOB=$(kubectl get jobs -o jsonpath='{.items[-1:].0.metadata.name}')
kubectl logs -l job-name=$JOB

# 7. (Optional) ingress
kubectl apply -f k8s/ingress.yaml
```

Troubleshooting:

| Problem | Fix |
|---------|-----|
| ImagePullBackOff for job | Ensure `simple-check-app:latest` loaded/pushed; image name matches handler param |
| `/run-check` returns 503 | Remove `-Dk8s.disabled=true`, apply RBAC, confirm cluster creds |
| Job stuck active | Inspect pod events `kubectl describe job/<name>` / pod logs |
| Timeout in script | Increase `TOTAL_TIMEOUT` env or `--timeout` arg |

### Local Script Test Against Cluster Service

If port-forwarding service:

```bash
kubectl port-forward svc/practice-service 8081:80 &
python3 simple-check/check.py --url http://localhost:8081/health/ready --timeout 15
```

### Testing `/run-check`

Unit test `RunCheckEndpointTest` asserts the `503` behavior when Kubernetes integration is disabled.
To test success manually in a cluster:

1. Build & load images (`practice-app:1.0.0` and `simple-check-app`).
2. Apply RBAC (`k8s/rbac.yaml`).
3. Deploy the app (`k8s/deployment.yaml` references the ServiceAccount).
4. (Optional) Apply `k8s/service.yaml` & `k8s/ingress.yaml` or just port-forward.
5. Call the endpoint through port-forward or ingress:

```bash
curl -X POST http://localhost:<forwarded-port>/run-check
```

Check created Job:

```bash
kubectl get jobs | grep simple-check-job
```

## Troubleshooting

| Symptom | Action |
|---------|--------|
| Port busy on 8080 | `lsof -nP -iTCP:8080 -sTCP:LISTEN` then `kill <pid>`; app auto-retries ephemeral port if busy |
| Readiness false | Hit `/health/ready`; ensure server fully started |
| YAML parser warning | Harmless; YAML module included so config loads |
| 503 from /run-check | RBAC not applied, image missing, or `-Dk8s.disabled=true` set |
| Job created but pods ImagePullBackOff | Image not present in cluster; load or push it |

### Port 8080 Already In Use

If the configured port can't bind (BindException) the app logs a warning and retries with an ephemeral port. To explicitly free 8080:

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
kill <pid>
lsof -nP -iTCP:8080 -sTCP:LISTEN || echo 'Port 8080 free'
gradle run
```

Force a different port without editing YAML:

```bash
gradle run -Dserver.port=9000
```

## License

Internal practice project
