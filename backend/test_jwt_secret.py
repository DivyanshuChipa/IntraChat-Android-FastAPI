import os
import sys

# Mock imports since we only want to test the environment variable loading
from unittest.mock import Mock
sys.modules['fastapi'] = Mock()
sys.modules['fastapi.middleware.cors'] = Mock()
sys.modules['fastapi.responses'] = Mock()
sys.modules['fastapi.staticfiles'] = Mock()
sys.modules['pydantic'] = Mock()
sys.modules['jose'] = Mock()

# Mock internal dependencies
for mod in ['chat', 'files', 'calls', 'messages', 'profiles', 'users', 'admin_api']:
    sys.modules[mod] = Mock()

def test_secret_key_from_env():
    # Set the environment variable
    os.environ['JWT_SECRET_KEY'] = 'MY_SECURE_TEST_KEY_123'

    # Import server module
    import server

    assert server.SECRET_KEY == 'MY_SECURE_TEST_KEY_123'
    print("Test passed: SECRET_KEY is loaded from environment variable")

def test_secret_key_default():
    # Ensure the environment variable is not set
    if 'JWT_SECRET_KEY' in os.environ:
        del os.environ['JWT_SECRET_KEY']

    # Reload server module
    import importlib
    import server
    importlib.reload(server)

    assert server.SECRET_KEY == 'CHANGE_THIS_TO_SOMETHING_RANDOM_AND_LONG'
    print("Test passed: SECRET_KEY falls back to default when env var is not set")

if __name__ == '__main__':
    test_secret_key_from_env()
    test_secret_key_default()
