import sys
from unittest.mock import MagicMock
import os
import pytest

# Mocking passlib since it is not installed in the environment
mock_passlib = MagicMock()
mock_passlib.hash.pbkdf2_sha256.hash.side_effect = lambda x: f"hashed_{x}"
mock_passlib.hash.pbkdf2_sha256.verify.side_effect = lambda pw, h: h == f"hashed_{pw}"

sys.modules["passlib"] = mock_passlib
sys.modules["passlib.hash"] = mock_passlib.hash

@pytest.fixture(autouse=True)
def setup_test_db(monkeypatch, tmp_path):
    # Set a temporary database for testing
    test_db = tmp_path / "test_users.db"

    # Patch the DATABASE_NAME in backend.users
    import backend.users
    monkeypatch.setattr(backend.users, "DATABASE_NAME", str(test_db))

    # Initialize the database
    backend.users.init_db()

    yield str(test_db)

    # Cleanup (handled by tmp_path)
