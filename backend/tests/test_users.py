import pytest
from backend.users import register_user, verify_user, set_require_approval

def test_register_user_success():
    """Test that a new user can be registered successfully."""
    username = "new_user"
    password = "secure_password"

    response = register_user(username, password)

    assert response["success"] is True
    assert response["message"] == "User registered successfully"

def test_register_user_duplicate():
    """Test that registering a duplicate username fails and returns the correct error message."""
    username = "duplicate_user"
    password = "password123"

    # Register the user for the first time
    first_response = register_user(username, password)
    assert first_response["success"] is True

    # Try to register the same username again
    second_response = register_user(username, password)

    assert second_response["success"] is False
    assert second_response["message"] == "Username already taken"

def test_register_user_approval_needed(monkeypatch):
    """Test registration when admin approval is required."""
    # Enable require approval
    set_require_approval(True)

    username = "waiting_user"
    password = "waiting_password"

    response = register_user(username, password)

    assert response["success"] is True
    assert response["message"] == "Registration successful! Wait for Admin approval."

def test_verify_user_success():
    """Test verify_user with valid credentials and approved user."""
    username = "test_user_ok"
    password = "test_password"
    register_user(username, password)

    response = verify_user(username, password)
    assert response == {"status": "OK"}

def test_verify_user_pending():
    """Test verify_user with valid credentials but pending approval."""
    set_require_approval(True)
    username = "test_user_pending"
    password = "test_password"
    register_user(username, password)

    response = verify_user(username, password)
    assert response == {"status": "PENDING"}

def test_verify_user_invalid_password():
    """Test verify_user with an incorrect password."""
    username = "test_user_wrong_pw"
    password = "test_password"
    register_user(username, password)

    response = verify_user(username, "wrong_password")
    assert response == {"status": "INVALID"}

def test_verify_user_nonexistent():
    """Test verify_user with a non-existent username."""
    response = verify_user("nonexistent_user", "any_password")
    assert response == {"status": "INVALID"}
