# Practice Helidon SE App

Small Helidon SE web server exposing:
- Greeting endpoint `/`
- Config dump `/config`
- Health endpoints `/health`, `/health/live`, `/health/ready` (custom readiness flag)
- Built with Gradle + Shadow JAR

## Source Highlights
- Main app: [src/main/java/com/example/SimpleWebServer.java](src/main/java/com/example/SimpleWebServer.java)
- Config: [src/main/resources/application.yaml](src/main/resources/application.yaml)
- Build script: [build.gradle](build.gradle)
- Test: [src/test/java/com/example/SimpleWebServerTest.java](src/test/java/com/example/SimpleWebServerTest.java)
- Container: [Dockerfile](Dockerfile)
- K8s manifests: [deployment.yaml](deployment.yaml), [service.yaml](service.yaml), [ingress.yaml](ingress.yaml)

## Requirements
- JDK 17+
- Gradle (wrapper or local)
- Docker (for container build)
- kubectl + a Kubernetes cluster (for K8s deploy)

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
Edit [application.yaml](src/main/resources/application.yaml):
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

### Suggested Probe Improvement
Current probes in [deployment.yaml](deployment.yaml) use `/`. For stronger health semantics you can switch to:
```yaml
livenessProbe:
  httpGet: { path: /health/live, port: 8080 }
readinessProbe:
  httpGet: { path: /health/ready, port: 8080 }
```

Apply update:
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
- Standard: build/libs/practice-1.0-SNAPSHOT.jar
- Fat (all deps): build/libs/practice-1.0-SNAPSHOT-all.jar (preferred for java -jar / Docker)

## Cleanup
```bash
docker rm -f practice 2>/dev/null || true
kubectl delete -f ingress.yaml -f service.yaml -f deployment.yaml 2>/dev/null || true
```

## Troubleshooting
| Symptom | Action |
|---------|--------|
| Port busy | Change `server.port` or use `SERVER_PORT` override (Helidon supports config overrides via env) |
| Readiness false | Hit `/health/ready`; ensure server fully started (flag set after start) |
| YAML parser warning in tests | Harmless; config still loaded via default sources |

## License
Internal practice project