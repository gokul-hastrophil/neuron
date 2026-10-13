"""Neuron engineering controller. Internal API: keep behind a private network."""
import os
import secrets
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone

import httpx
import psycopg
from github_auth import configured_credentials
from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

DATABASE_URL = os.environ["DATABASE_URL"]
TOKEN = os.environ["CONTROLLER_TOKEN"]
REPO = os.getenv("GITHUB_REPO", "gokul-hastrophil/neuron")
if len(TOKEN) < 32:
    raise RuntimeError("CONTROLLER_TOKEN must be at least 32 characters")

SCHEMA = """
CREATE TABLE IF NOT EXISTS engineering_jobs (
  id BIGSERIAL PRIMARY KEY,
  issue_number INTEGER NOT NULL UNIQUE,
  title TEXT NOT NULL,
  state TEXT NOT NULL DEFAULT 'queued'
    CHECK (state IN ('queued','leased','completed','failed')),
  worker_id TEXT,
  lease_token TEXT,
  leased_until TIMESTAMPTZ,
  attempts INTEGER NOT NULL DEFAULT 0,
  result TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
"""

def db():
    return psycopg.connect(DATABASE_URL)

def authorize(authorization: str = Header(default="")):
    if not authorization.startswith("Bearer ") or not secrets.compare_digest(
        authorization[7:], TOKEN
    ):
        raise HTTPException(401, "Unauthorized")

@asynccontextmanager
async def lifespan(app):
    with db() as conn:
        conn.execute(SCHEMA)
    yield

app = FastAPI(title="Neuron Engineering Controller", lifespan=lifespan)

class Claim(BaseModel):
    worker_id: str = Field(min_length=3, max_length=120)

class Finish(BaseModel):
    worker_id: str
    lease_token: str
    success: bool
    result: str = Field(max_length=8000)

@app.get("/health")
def health():
    with db() as conn:
        conn.execute("SELECT 1").fetchone()
    return {"status": "ok"}

@app.post("/sync", dependencies=[Depends(authorize)])
async def sync():
    """Import ONLY issues explicitly labeled agent-ready."""
    credentials = configured_credentials()
    if not credentials:
        raise HTTPException(503, "GitHub App credentials not configured")
    github_token = credentials.installation_token()
    headers = {"Authorization": f"Bearer {github_token}",
               "Accept": "application/vnd.github+json",
               "X-GitHub-Api-Version": "2022-11-28"}
    count = 0
    async with httpx.AsyncClient(timeout=20) as client:
        page = 1
        while True:
            response = await client.get(
                f"https://api.github.com/repos/{REPO}/issues",
                params={"state": "open", "labels": "agent-ready",
                        "per_page": 100, "page": page},
                headers=headers,
            )
            response.raise_for_status()
            items = response.json()
            with db() as conn:
                for issue in items:
                    if "pull_request" in issue:
                        continue
                    conn.execute(
                        """INSERT INTO engineering_jobs (issue_number,title)
                           VALUES (%s,%s) ON CONFLICT (issue_number) DO NOTHING""",
                        (issue["number"], issue["title"]),
                    )
                    count += 1
            if len(items) < 100:
                break
            page += 1
            if page > 10:
                break
    return {"observed_issues": count}

@app.post("/jobs/claim", dependencies=[Depends(authorize)])
def claim(payload: Claim):
    token = secrets.token_urlsafe(32)
    with db() as conn:
        # Row locking ensures that only one worker receives each job.
        row = conn.execute(
            """SELECT id,issue_number,title FROM engineering_jobs
               WHERE state='queued' OR
                 (state='leased' AND leased_until < now())
               ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1"""
        ).fetchone()
        if not row:
            return {"job": None}
        conn.execute(
            """UPDATE engineering_jobs SET state='leased', worker_id=%s,
               lease_token=%s, leased_until=now()+interval '15 minutes',
               attempts=attempts+1, updated_at=now() WHERE id=%s""",
            (payload.worker_id, token, row[0]),
        )
        return {"job": {"id": row[0], "issue_number": row[1],
                        "title": row[2], "lease_token": token,
                        "lease_seconds": 900}}

@app.post("/jobs/{job_id}/heartbeat", dependencies=[Depends(authorize)])
def heartbeat(job_id: int, payload: Finish):
    with db() as conn:
        row = conn.execute(
            """UPDATE engineering_jobs SET leased_until=now()+interval '15 minutes',
               updated_at=now() WHERE id=%s AND state='leased'
               AND worker_id=%s AND lease_token=%s
               AND leased_until>now() RETURNING id""",
            (job_id, payload.worker_id, payload.lease_token),
        ).fetchone()
    if not row:
        raise HTTPException(409, "Lease no longer valid")
    return {"ok": True}

@app.post("/jobs/{job_id}/finish", dependencies=[Depends(authorize)])
def finish(job_id: int, payload: Finish):
    with db() as conn:
        row = conn.execute(
            """UPDATE engineering_jobs SET state=%s, result=%s,
               lease_token=NULL, leased_until=NULL, updated_at=now()
               WHERE id=%s AND state='leased' AND worker_id=%s
               AND lease_token=%s AND leased_until>now()
               RETURNING issue_number""",
            ("completed" if payload.success else "failed", payload.result,
             job_id, payload.worker_id, payload.lease_token),
        ).fetchone()
    if not row:
        raise HTTPException(409, "Lease no longer valid")
    return {"ok": True, "issue_number": row[0]}

@app.get("/jobs", dependencies=[Depends(authorize)])
def list_jobs():
    with db() as conn:
        rows = conn.execute(
            """SELECT id,issue_number,title,state,worker_id,attempts
               FROM engineering_jobs ORDER BY id DESC LIMIT 100"""
        ).fetchall()
    return {"jobs": [dict(zip(
        ("id","issue_number","title","state","worker_id","attempts"), r
    )) for r in rows]}
