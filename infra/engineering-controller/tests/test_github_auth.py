"""Offline unit tests; run: python -m unittest discover -s tests -v"""
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from github_auth import GitHubAppCredentials


class TestGitHubAppCredentials(unittest.TestCase):
    def test_jwt_claims(self):
        from cryptography.hazmat.primitives.asymmetric import rsa
        from cryptography.hazmat.primitives import serialization
        import jwt
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        pem = key.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption(),
        )
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "app.pem"
            path.write_bytes(pem)
            credentials = GitHubAppCredentials("123", "456", str(path))
            encoded = credentials.app_jwt(now=1_800_000_000)
            decoded = jwt.decode(
                encoded, key.public_key(), algorithms=["RS256"],
                options={"verify_exp": False, "verify_iat": False},
            )
            self.assertEqual(decoded["iss"], "123")
            self.assertEqual(decoded["iat"], 1_799_999_940)
            self.assertEqual(decoded["exp"], 1_800_000_540)

    def test_cached_token_does_not_make_request(self):
        credentials = GitHubAppCredentials("1", "2", "/missing")
        credentials._token = "cached"
        credentials._expires_at = 10**12
        with patch("github_auth.httpx.Client") as client:
            self.assertEqual(credentials.installation_token(), "cached")
            client.assert_not_called()


if __name__ == "__main__":
    unittest.main()
