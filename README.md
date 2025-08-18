# Stage 4: Introduce basic Kubernetes manifests (Deployment, Service, Ingress)

Concepts introduced:
- Stateless Deployment with 2 replicas
- ClusterIP Service exposing app internally on port 80 -> 8080
- Ingress routing external traffic to Service (host: practice.local)
- Health probe alignment using /health/live and /health/ready

Key files:
- k8s/deployment.yaml
- k8s/service.yaml
- k8s/ingress.yaml

Try it (after building image):
```bash
docker build -t practice-app:1.0.0 .
kubectl apply -f k8s/deployment.yaml -f k8s/service.yaml -f k8s/ingress.yaml
# Verify
kubectl get pods -l app=practice-app
```

Next: improve server robustness (port fallback) & enrich README explanations.
