"""Short-lived GitHub App installation tokens; no PAT required."""
import os
import time
from pathlib import Path

import httpx
import jwt


class GitHubAppCredentials:
    def __init__(self, app_id: str, installation_id: str, key_path: str):
        self.app_id = str(app_id)
        self.installation_id = str(installation_id)
        self.key_path = Path(key_path)
        self._token = None
        self._expires_at = 0.0

    def app_jwt(self, now=None):
        now = int(time.time() if now is None else now)
        private_key = self.key_path.read_bytes()
        return jwt.encode(
            {"iat": now - 60, "exp": now + 540, "iss": self.app_id},
            private_key,
            algorithm="RS256",
        )

    def installation_token(self):
        # Refresh before expiry, never log bearer tokens.
        if self._token and time.time() < self._expires_at - 300:
            return self._token
        with httpx.Client(timeout=20) as client:
            response = client.post(
                f"https://api.github.com/app/installations/{self.installation_id}/access_tokens",
                headers={
                    "Authorization": f"Bearer {self.app_jwt()}",
                    "Accept": "application/vnd.github+json",
                    "X-GitHub-Api-Version": "2022-11-28",
                },
            )
            response.raise_for_status()
            data = response.json()
        from datetime import datetime
        self._token = data["token"]
        self._expires_at = datetime.fromisoformat(
            data["expires_at"].replace("Z", "+00:00")
        ).timestamp()
        return self._token


def configured_credentials():
    required = ("GITHUB_APP_ID", "GITHUB_INSTALLATION_ID", "GITHUB_APP_PRIVATE_KEY_PATH")
    if not all(os.environ.get(k) for k in required):
        return None
    return GitHubAppCredentials(*(os.environ[k] for k in required))
