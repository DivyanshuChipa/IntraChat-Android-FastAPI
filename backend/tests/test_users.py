import pytest
from backend.users import register_user

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
    from backend.users import set_require_approval

    # Enable require approval
    set_require_approval(True)

    username = "waiting_user"
    password = "waiting_password"

    response = register_user(username, password)

    assert response["success"] is True
    assert response["message"] == "Registration successful! Wait for Admin approval."
