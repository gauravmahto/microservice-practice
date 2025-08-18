# Stage 6: Build & distribution tooling (source compression)

Added:
- scripts/compress-source.sh for creating clean source archives (tracked files)
- Concept: reproducible source packaging respecting .gitignore

Usage:
```bash
./scripts/compress-source.sh
./scripts/compress-source.sh --include-untracked
```

Next: integrate optional Kubernetes Job trigger endpoint (/run-check) with RBAC.
