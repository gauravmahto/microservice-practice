# Stage 5: Server robustness & improved health probe alignment

Enhancements added:
- Port binding retry: if configured port is busy, fall back to ephemeral port 0
- Expanded README with probe configuration guidance
- Clearer config dump output (flattened key=value)

Key learning points:
- Operational resilience (port conflicts)
- Production-grade health probe configuration

Next: add build/tooling enhancements (source compression script).
