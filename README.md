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

## Source Highlights

* Main app: [`SimpleWebServer.java`](src/main/java/com/example/SimpleWebServer.java)
* Config: [`application.yaml`](src/main/resources/application.yaml)
* Build script: [`build.gradle`](build.gradle)
* Test: [`SimpleWebServerTest.java`](src/test/java/com/example/SimpleWebServerTest.java)
* Container: [`Dockerfile`](Dockerfile)
* K8s manifests: [`deployment.yaml`](deployment.yaml), [`service.yaml`](service.yaml), [`ingress.yaml`](ingress.yaml)

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

Apply manifests (adjust image tag to one you built/pushed):

```bash
# (Optional) load image into local cluster (example for kind)
# kind load docker-image practice-app:1.0.0

kubectl apply -f deployment.yaml -f service.yaml -f ingress.yaml

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
kubectl apply -f deployment.yaml
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
kubectl delete -f ingress.yaml -f service.yaml -f deployment.yaml 2>/dev/null || true
```

## Troubleshooting

| Symptom | Action |
|---------|--------|
| Port busy on 8080 | `lsof -nP -iTCP:8080 -sTCP:LISTEN` then `kill <pid>`; app auto-retries ephemeral port if busy |
| Readiness false | Hit `/health/ready`; ensure server fully started |
| YAML parser warning | Harmless; YAML module included so config loads |

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
