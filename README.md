# Stage 3: Basic Helidon web server with health endpoints and config support

This stage replaces the HelloWorld class with a real Helidon SE `SimpleWebServer`.

Concepts introduced:
- Helidon WebServer + Routing
- Health endpoints (/health, /health/live, /health/ready) with readiness flag
- Externalized config via application.yaml (port, greeting)
- Basic JUnit 5 test exercising root + health endpoints

Key files:
- src/main/java/com/example/SimpleWebServer.java
- src/main/resources/application.yaml
- src/test/java/com/example/SimpleWebServerTest.java
- build.gradle (adds Helidon + shadow plugin)

Try it:
```bash
gradle run
# In another terminal
curl -s localhost:8080/health
```

Next: introduce Kubernetes manifests (Deployment, Service, Ingress).
