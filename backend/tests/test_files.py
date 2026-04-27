import pytest
import os
import sys
from unittest.mock import MagicMock

# Mock fastapi before importing backend.files
mock_fastapi = MagicMock()
sys.modules["fastapi"] = mock_fastapi
sys.modules["fastapi.responses"] = MagicMock()

from backend.files import _safe_filename

def test_safe_filename_basic():
    assert _safe_filename("test.txt") == "test.txt"
    assert _safe_filename("image.png") == "image.png"

def test_safe_filename_path_traversal():
    # os.path.basename("../config.py") is "config.py"
    assert _safe_filename("../config.py") == "config.py"
    assert _safe_filename("/etc/passwd") == "passwd"

def test_safe_filename_edge_cases():
    # These currently return ".." and "." with os.path.basename
    # We want them to be sanitized to something safe or empty.
    assert _safe_filename("..") == ""
    assert _safe_filename(".") == ""
    assert _safe_filename("") == ""

def test_safe_filename_complex():
    assert _safe_filename("   ") == ""
    assert _safe_filename("nested/dir/file.txt") == "file.txt"
