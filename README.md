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
* **Dynamic configuration generation using Ansible init container** - generates application config from Helm values before the main container starts
* **Local Observability Stack** - Ansible playbook to spin up Prometheus and Grafana locally for monitoring

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
* Helm 3+ (for Kubernetes deployment via Helm chart)
* Minikube or similar (for local Kubernetes testing)

## Documentation

* Architecture: `ARCHITECTURE.md`
* Deployment: `DEPLOYMENT.md`
* Configuration: `CONFIGURATION.md`
* Operations: `OPERATIONS.md`
* Troubleshooting: `TROUBLESHOOTING.md`
* Decommission: `DECOMMISSION.md`

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
# Root endpoint
curl -s localhost:8080/

# Greeting endpoint (configured message)
curl -s localhost:8080/greet

# Configuration dump (server.* and app.* keys)
curl -s localhost:8080/config

# Health endpoints
curl -s localhost:8080/health
curl -s localhost:8080/health/live
curl -s localhost:8080/health/ready

# Create Kubernetes Job (requires cluster access and RBAC)
curl -s -X POST localhost:8080/run-check -H 'Content-Type: application/json' -d '{}'
# The /run-check endpoint accepts a JSON body with overrides; see "RunCheck" section below
```

## Docker

### Building Images

This project requires two Docker images:

### Main Application Image

```bash
# Build the main application image
docker build --load -t practice-app:1.0.0 .
```

### Ansible Configuration Runner Image (for init container)

```bash
# Build the Ansible config generator image
cd ansible
docker build --load -t practice-ansible-config-runner:1.0.0 .
cd ..
```

### Running Locally with Docker

```bash
# Run main application container
docker run --rm -p 8080:8080 --name practice practice-app:1.0.0

# Smoke test
curl -s localhost:8080/health
```

## Local Observability Stack

This project includes an Ansible playbook to provision a local observability stack using **Prometheus** and **Grafana** running in **Podman** containers. This allows you to monitor the application metrics locally.

### Prerequisites

* **Podman**: Must be installed and running.
* **Ansible**: Must be installed with the `containers.podman` collection.

  ```bash
  ansible-galaxy collection install containers.podman
  ```

### Setup

To provision the stack (Prometheus + Grafana) and create the necessary configurations:

```bash
ansible-playbook ansible/playbooks/observability.yml
```

This will:

1. Generate Prometheus and Grafana configurations in `ansible/generated_config/`.
2. Create a Podman network named `monitoring`.
3. Start a **Prometheus** container (scraping `host.docker.internal:8080`).
4. Start a **Grafana** container with a pre-configured dashboard.

### Access

* **Prometheus**: [http://localhost:9090](http://localhost:9090)
* **Grafana**: [http://localhost:3000](http://localhost:3000)
  * **User**: `admin`
  * **Password**: `admin`

### Teardown

To stop and remove the containers and network:

```bash
ansible-playbook ansible/playbooks/teardown.yml
```

## Helm Chart Deployment

The project includes a Helm chart (`practice-chart/`) that deploys the application with an init container for dynamic configuration generation.

### Architecture Overview

The deployment uses an **init container pattern**:

1. **Init Container** (`ansible-config-generator`):
   * Runs Ansible playbook to generate application configuration
   * Writes `config.json` to a shared volume (`config-volume`)
   * Uses values from `values.yaml` as environment variables

2. **Main Container** (`practice-app`):
   * Starts after init container completes successfully
   * Mounts the shared volume at `/app/config` (read-only)
   * Reads the generated configuration file

3. **Shared Volume** (`config-volume`):
   * EmptyDir volume shared between init and main containers
   * Persists only for the pod lifetime

### Helm Chart Configuration

Key configuration in `practice-chart/values.yaml`:

```yaml
# Main application image
image:
  repository: localhost/practice-app  # Use localhost/ prefix for Minikube
  tag: "1.0.0"
  pullPolicy: Never  # For local images in Minikube

# Ansible config generator for init container
configGenerator:
  image:
    repository: localhost/practice-ansible-config-runner
    tag: "1.0.0"

# Application configuration passed to init container
applicationConfig:
  greeting: "Hello from Helm!"
  features:
    dashboard: true
    apiV2: false
```

### Complete Deployment from Scratch

#### Step 1: Start Minikube

```bash
# Start Minikube with the driver it was created with
minikube start

# Verify cluster is running
kubectl cluster-info
kubectl get nodes
```

#### Step 2: Build the Application

```bash
# Build Java application
./gradlew clean build -x test
```

#### Step 3: Build Docker Images

```bash
# Build main application image
docker build --load -t practice-app:1.0.0 .

# Build Ansible config runner image
cd ansible
docker build --load -t practice-ansible-config-runner:1.0.0 .
cd ..

# Verify images are built
docker images | grep practice
```

#### Step 4: Load Images into Minikube

```bash
# Load both images into Minikube
minikube image load practice-app:1.0.0
minikube image load practice-ansible-config-runner:1.0.0

# Verify images in Minikube
minikube ssh "sudo crictl images | grep practice"
```

**Note**: Minikube stores images with the `localhost/` prefix internally. The Helm values.yaml uses `localhost/practice-app` and `localhost/practice-ansible-config-runner` to match this.

#### Step 5: Deploy with Helm

```bash
# Install the Helm chart
helm install practice-release ./practice-chart

# Or upgrade if already installed
helm upgrade practice-release ./practice-chart

# Watch pods starting up
kubectl get pods -l app.kubernetes.io/name=practice-chart -w
```

#### Step 6: Verify Deployment

```bash
# Check pod status (should show Running after init container completes)
kubectl get pods -l app.kubernetes.io/name=practice-chart

# View init container logs (Ansible playbook execution)
kubectl logs <pod-name> -c ansible-config-generator

# Verify generated configuration file
kubectl exec <pod-name> -- cat /app/config/config.json

# Check main application logs
kubectl logs <pod-name> -c practice-chart
```

Expected output from config file:

```json
{
  "greetingMessage": "Hello from Helm!",
  "featureFlags": {
    "enableNewDashboard": true,
    "enableApiV2": false
  }
}
```

#### Step 7: Test the Application

```bash
# Port-forward to access the application
kubectl port-forward svc/practice-release-practice-chart 8080:8080

# In another terminal, test the available endpoints
curl http://localhost:8080                    # Root endpoint - returns "OK"
curl http://localhost:8080/greet             # Greeting from config
curl http://localhost:8080/config            # Application configuration
curl http://localhost:8080/health            # Health check
curl http://localhost:8080/health/live       # Liveness probe
curl http://localhost:8080/health/ready      # Readiness probe
```

**Expected responses:**

* `/` - Returns `OK`
* `/greet` - Returns the greeting message from config (e.g., "Hello from Helm!")
* `/config` - Returns JSON with `app.*` and `server.*` configuration
* `/health` - Returns overall health status
* `/health/live` - Returns liveness status
* `/health/ready` - Returns readiness status

### Helm Commands Reference

```bash
# Validate chart syntax
helm lint ./practice-chart

# Render templates without installing (dry-run)
helm template test-release ./practice-chart

# Render and view specific sections
helm template test-release ./practice-chart | grep -A 20 "initContainers:"
helm template test-release ./practice-chart | grep -A 10 "volumes:"

# Install/upgrade with custom values
helm upgrade --install practice-release ./practice-chart \
  --set applicationConfig.greeting="Custom greeting" \
  --set applicationConfig.features.dashboard=false

# View deployed release
helm list
helm get values practice-release
helm get manifest practice-release

# Uninstall
helm uninstall practice-release
```

### Troubleshooting Helm Deployment

#### Init Container Issues

**Problem**: Init container in `CrashLoopBackOff` or `Error` state

```bash
# Check init container logs
kubectl logs <pod-name> -c ansible-config-generator

# Common issues:
# 1. Template file not found - ensure ansible/templates/config.json.j2 exists
# 2. Playbook errors - check Ansible syntax in ansible/playbooks/generate_config.yml
```

**Problem**: Init container in `ImageInspectError` or `ImagePullBackOff`

```bash
# Verify images are loaded in Minikube
minikube ssh "sudo crictl images | grep practice"

# If missing, reload images
minikube image load practice-app:1.0.0
minikube image load practice-ansible-config-runner:1.0.0

# Delete pods to force recreation
kubectl delete pods -l app.kubernetes.io/name=practice-chart
```

**Problem**: Image names not matching

* Minikube stores local images with `localhost/` prefix
* Ensure `values.yaml` uses:
  * `image.repository: localhost/practice-app`
  * `configGenerator.image.repository: localhost/practice-ansible-config-runner`
  * `image.pullPolicy: Never`

#### Main Container Issues

**Problem**: Main container not starting after init container completes

```bash
# Check pod events
kubectl describe pod <pod-name>

# Check if volume mount is correct
kubectl exec <pod-name> -- ls -la /app/config/

# Verify config file exists
kubectl exec <pod-name> -- cat /app/config/config.json
```

#### Helm Release Issues

**Problem**: Release fails to upgrade

```bash
# Check Helm release status
helm status practice-release

# View release history
helm history practice-release

# Rollback to previous version if needed
helm rollback practice-release <revision>

# Force reinstall
helm uninstall practice-release
helm install practice-release ./practice-chart
```

### Debugging Commands

```bash
# Get detailed pod information
kubectl describe pod <pod-name>

# View all container logs in a pod
kubectl logs <pod-name> --all-containers=true

# Execute commands inside running container
kubectl exec -it <pod-name> -- /bin/sh

# View pod resource usage
kubectl top pod <pod-name>

# Check events in namespace
kubectl get events --sort-by='.lastTimestamp'

# Inspect deployment
kubectl describe deployment practice-release-practice-chart

# View generated deployment YAML
kubectl get deployment practice-release-practice-chart -o yaml
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
kubectl get pods -l app.kubernetes.io/instance=my-app
kubectl get svc my-app-practice-chart
```

Port-forward (if no ingress controller):

```bash
# forward the application service to localhost:8080 (service exposes port 8080)
kubectl -n practice port-forward svc/my-app-practice-chart 8080:8080
curl -s http://localhost:8080/health/ready
```

Notes:

* The service name generated by the Helm chart is typically `my-app-practice-chart` for the current release; adjust the `kubectl` commands if you use a different release name.
* If your Deployment uses `image.pullPolicy: IfNotPresent` and the node already has the same image tag cached, Kubernetes will not re-pull the image even if the registry image digest changed. Use an immutable tag (recommended), set `image.pullPolicy: Always`, or deploy by digest (image@sha256:...) to guarantee the exact image is used.

Flux/HelmRelease note:

* This repository uses Flux to reconcile a `HelmRelease` under `clusters/practice/releases/practice-app.yaml`. We set `releaseName: my-app` in that HelmRelease so Flux upgrades the existing Helm release named `my-app` instead of creating a differently-named release. To trigger upgrades, push commits that change the HelmRelease `values.image.tag` (or set a digest) and Flux will apply the change automatically.

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
