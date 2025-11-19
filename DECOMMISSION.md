# Decommission Guide

This guide documents how to cleanly tear down the Helm deployment and related local resources used during development and testing.

## Prerequisites

- `kubectl` and `helm` configured to the target cluster (e.g., Minikube)
- If you started a local port-forward, the process may still be running in a terminal

## 1) Stop Local Port-Forward (if running)

If you started a background port-forward, stop it (adjust PID/path if different):

```bash
# If you saved the PID like this:
#   kubectl port-forward svc/practice-release-practice-chart 8080:8080 >/tmp/pf_practice.log 2>&1 & echo $! > /tmp/pf_practice.pid
# then stop it with:
if [ -f /tmp/pf_practice.pid ]; then kill "$(cat /tmp/pf_practice.pid)" || true; rm -f /tmp/pf_practice.pid; fi
```

If you ran port-forward in the foreground, press Ctrl+C in that terminal.

## 2) Uninstall the Helm Release

```bash
helm uninstall practice-release
```

Verify resources are gone:

```bash
kubectl get all -l app.kubernetes.io/name=practice-chart || true
# You should see: "No resources found" (or no matches)
```

You can also double-check no release entries remain:

```bash
helm list --all-namespaces | grep practice-release || echo "No release named practice-release"
```

## 3) Optional: Remove Local Images from Minikube

If you loaded images into Minikube for local testing and want to clean them up:

```bash
# Remove images from the Minikube node image cache (adjust tags if needed)
minikube image rm localhost/practice-app:1.0.0 || true
minikube image rm localhost/practice-ansible-config-runner:1.0.0 || true
```

This does not delete images from your host Docker; to remove those, run on your host:

```bash
docker rmi practice-app:1.0.0 practice-ansible-config-runner:1.0.0 2>/dev/null || true
```

## 4) Optional: Remove Flux Resources (if you deployed via Flux)

If you installed via Flux (GitOps), remove the `HelmRelease` and let Flux reconcile:

```bash
# Remove the HelmRelease manifest from your Git repo path watched by Flux
# Commit and push, then reconcile Flux or wait for automatic reconciliation
```

## 5) Quick Sanity Checks

```bash
# No pods/services/deployments should be present for the chart
kubectl get pods,svc,deploy -A | egrep 'practice-chart|practice-release' || echo "No k8s resources with practice labels"
```

---
If your workflow differs (custom namespace, different release name, alternative service name), adjust commands accordingly.
