"""Simple deterministic readiness check script.

This script performs an HTTP GET against a target URL (expected to be the
application readiness endpoint) and exits 0 only if the endpoint returns
HTTP 200 and the JSON payload contains overall status UP.

Configuration precedence (first found wins):
1. CLI arg --url
2. Environment variable TARGET_URL
3. Built from (TARGET_HOST, TARGET_PORT, TARGET_PATH) env vars
4. Fallback default: http://practice-service/health/ready

The script retries for up to TOTAL_TIMEOUT seconds (default 30) with a
short exponential backoff to allow the application to become ready.
"""

from __future__ import annotations

import json
import os
import sys
import time
import urllib.request
import argparse
from typing import Optional

DEFAULT_FALLBACK_URL = "http://practice-service/health/ready"


def build_url(args) -> str:
    if args.url:
        return args.url
    env_url = os.getenv("TARGET_URL")
    if env_url:
        return env_url
    # Optional granular env vars (TARGET_HOST, TARGET_PORT, TARGET_PATH) may be used
    # to construct the URL; otherwise fall back to DEFAULT_FALLBACK_URL.
    host = os.getenv("TARGET_HOST")
    port = os.getenv("TARGET_PORT")
    path = os.getenv("TARGET_PATH")
    if host:
        if not path:
            path = "/health/ready"
        if not path.startswith("/"):
            path = "/" + path
        if port:
            return f"http://{host}:{port}{path}"
        else:
            return f"http://{host}{path}"
    return DEFAULT_FALLBACK_URL


def fetch(url: str) -> tuple[int, Optional[str]]:
    try:
        # nosec - controlled internal URL
        with urllib.request.urlopen(url, timeout=5) as resp:
            status = resp.getcode()
            body = resp.read().decode("utf-8", errors="replace")
            return status, body
    except Exception as e:  # broad to simplify retry logic
        return -1, str(e)


def parse_status(body: str) -> Optional[str]:
    try:
        data = json.loads(body)
    except Exception:
        return None
    # Helidon health aggregate shape: {"status":"UP", "checks": [...]}
    return data.get("status") if isinstance(data, dict) else None


def main(argv) -> int:
    parser = argparse.ArgumentParser(
        description="Deterministic readiness check")
    parser.add_argument("--url", help="Full URL to check (overrides env)")
    parser.add_argument("--timeout", type=int, default=int(os.getenv("TOTAL_TIMEOUT", "30")),
                        help="Total timeout in seconds (default 30)")
    args = parser.parse_args(argv)

    url = build_url(args)
    deadline = time.time() + max(1, args.timeout)
    attempt = 0
    print(f"[check] Target URL: {url}")
    while time.time() < deadline:
        attempt += 1
        status_code, body = fetch(url)
        if status_code == 200:
            overall = parse_status(body or "")
            if overall == "UP":
                print(f"[check] SUCCESS (attempt {attempt}) - service ready")
                return 0
            else:
                print(
                    f"[check] attempt {attempt}: HTTP 200 but status not UP (parsed={overall})")
        else:
            print(
                f"[check] attempt {attempt}: HTTP={status_code} error={body}")
        # Backoff (capped)
        sleep_for = min(5, 0.5 * (2 ** (attempt - 1)))
        time.sleep(sleep_for)

    print(f"[check] FAILURE: service not ready within {args.timeout}s")
    return 1


if __name__ == "__main__":  # pragma: no cover
    sys.exit(main(sys.argv[1:]))
