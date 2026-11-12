#!/usr/bin/env python3
"""Bounded, authenticated, read-only load against ShipFlow's projects endpoint."""
import concurrent.futures
import json
import os
import statistics
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path

URL = os.environ.get("LOAD_URL", "http://127.0.0.1:18081/api/projects")
TOKEN_FILE = Path(os.environ.get("TOKEN_FILE", "/tmp/shipflow-load-token"))
DURATION = int(os.environ.get("LOAD_SECONDS", "60"))
CONCURRENCY = int(os.environ.get("LOAD_CONCURRENCY", "12"))

if DURATION < 1 or DURATION > 600:
    raise SystemExit("LOAD_SECONDS must be between 1 and 600")
if CONCURRENCY < 1 or CONCURRENCY > 20:
    raise SystemExit("LOAD_CONCURRENCY must be between 1 and 20")
if not TOKEN_FILE.is_file():
    raise SystemExit(f"Token file not found: {TOKEN_FILE}")
token = TOKEN_FILE.read_text().strip()
if not token:
    raise SystemExit("Token file is empty")

url = URL
headers = {"Authorization": f"Bearer {token}", "Accept": "application/json"}
deadline = time.monotonic() + DURATION
latencies_ms = []
status_counts = {}
errors = 0
lock = threading.Lock()


def worker():
    global errors
    own_latencies = []
    own_statuses = {}
    own_errors = 0
    while time.monotonic() < deadline:
        started = time.perf_counter()
        request = urllib.request.Request(url, headers=headers, method="GET")
        try:
            with urllib.request.urlopen(request, timeout=10) as response:
                response.read()
                own_statuses[response.status] = own_statuses.get(response.status, 0) + 1
        except Exception:
            own_errors += 1
        own_latencies.append((time.perf_counter() - started) * 1000)
    with lock:
        latencies_ms.extend(own_latencies)
        for code, count in own_statuses.items():
            status_counts[code] = status_counts.get(code, 0) + count
        errors += own_errors


started = time.monotonic()
with concurrent.futures.ThreadPoolExecutor(max_workers=CONCURRENCY) as executor:
    list(executor.map(lambda _: worker(), range(CONCURRENCY)))
elapsed = time.monotonic() - started
ordered = sorted(latencies_ms)


def percentile(value):
    index = max(0, min(len(ordered) - 1, int((len(ordered) - 1) * value)))
    return round(ordered[index], 2)


total = len(ordered)
print(json.dumps({
    "url": URL,
    "duration_seconds": round(elapsed, 2),
    "concurrency": CONCURRENCY,
    "requests": total,
    "requests_per_second": round(total / elapsed, 2) if elapsed else 0,
    "http_statuses": {str(code): count for code, count in sorted(status_counts.items())},
    "errors": errors,
    "error_percent": round(errors * 100 / total, 3) if total else 0,
    "p50_ms": percentile(0.50) if total else None,
    "p95_ms": percentile(0.95) if total else None,
    "p99_ms": percentile(0.99) if total else None,
}, ensure_ascii=False))
