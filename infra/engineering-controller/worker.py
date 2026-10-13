"""Ubuntu worker protocol example. Does not execute arbitrary issue text."""
import os
import socket
import time

import httpx

URL = os.environ["NEURON_CONTROLLER_URL"].rstrip("/")
TOKEN = os.environ["CONTROLLER_TOKEN"]
WORKER = os.getenv("WORKER_ID", socket.gethostname())
HEADERS = {"Authorization": f"Bearer {TOKEN}"}

def main():
    with httpx.Client(timeout=30, headers=HEADERS) as client:
        while True:
            try:
                response = client.post(f"{URL}/jobs/claim",
                                       json={"worker_id": WORKER})
                response.raise_for_status()
                job = response.json()["job"]
                if job:
                    print(f"Claimed issue #{job['issue_number']}: {job['title']}",
                          flush=True)
                    # Safe initial behavior: fail closed until sandboxed coding
                    # runner and review pipeline are explicitly configured.
                    response = client.post(
                        f"{URL}/jobs/{job['id']}/finish",
                        json={"worker_id": WORKER,
                              "lease_token": job["lease_token"],
                              "success": False,
                              "result": "Coding runner not configured; no code executed."},
                    )
                    response.raise_for_status()
            except (httpx.HTTPError, KeyError) as exc:
                print(f"Worker error: {exc}", flush=True)
            time.sleep(30)

if __name__ == "__main__":
    main()
