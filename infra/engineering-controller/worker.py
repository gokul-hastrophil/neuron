"""Read-only worker connectivity diagnostic; execution is not yet enabled."""
import os
import sys
import httpx


def main():
    if "--check" not in sys.argv[1:]:
        print("Worker execution disabled until sandboxed runner is integrated.", file=sys.stderr)
        return 78
    url = os.environ.get("NEURON_CONTROLLER_URL", "").rstrip("/")
    if not url:
        print("NEURON_CONTROLLER_URL is required.", file=sys.stderr)
        return 78
    try:
        response = httpx.get(f"{url}/health", timeout=10)
        response.raise_for_status()
        return 0 if response.json().get("status") == "ok" else 1
    except (httpx.HTTPError, ValueError):
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
