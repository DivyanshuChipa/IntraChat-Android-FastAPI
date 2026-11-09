import os
import sys
import shutil
import tempfile
import pytest

# Ensure systemctlmanger can be imported
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "systemctlmanger")))

from capability_manager import (
    CAPABILITIES,
    get_intra_tools_dir,
    ensure_tools_dir,
    inject_tools_to_path,
    detect_capability,
    uninstall_local_capability
)


def test_capabilities_definition():
    """Verify standard capabilities exist with proper config structure."""
    assert "ffmpeg" in CAPABILITIES
    assert "tesseract" in CAPABILITIES
    assert "yt-dlp" in CAPABILITIES

    for key, item in CAPABILITIES.items():
        assert "name" in item
        assert "binary_names" in item
        assert "download_url" in item
        assert "download_type" in item


def test_tools_directory_resolution(monkeypatch, tmp_path):
    """Verify tools directory honors LOCALAPPDATA when present."""
    fake_local = str(tmp_path / "AppDataLocal")
    monkeypatch.setenv("LOCALAPPDATA", fake_local)

    d = get_intra_tools_dir()
    expected = os.path.abspath(os.path.join(fake_local, "IntraServer", "bin"))
    assert d == expected

    created_d = ensure_tools_dir()
    assert os.path.isdir(created_d)


def test_inject_tools_to_path(monkeypatch, tmp_path):
    """Verify Intra tools directory is injected to PATH."""
    fake_local = str(tmp_path / "AppDataLocal")
    monkeypatch.setenv("LOCALAPPDATA", fake_local)

    tools_dir = get_intra_tools_dir()
    updated_path = inject_tools_to_path()
    assert tools_dir in updated_path.split(os.pathsep)


def test_detect_local_capability_and_uninstall(monkeypatch, tmp_path):
    """Test detection of locally installed tool and safe uninstallation."""
    fake_local = str(tmp_path / "AppDataLocal")
    monkeypatch.setenv("LOCALAPPDATA", fake_local)
    tools_dir = ensure_tools_dir()

    # Initially not installed
    detection_before = detect_capability("ffmpeg")
    # Might be installed on the Linux system or not, but local check will test our specific folder
    fake_ffmpeg = os.path.join(tools_dir, "ffmpeg.exe")
    with open(fake_ffmpeg, "w") as f:
        f.write("mock binary")

    detection_after = detect_capability("ffmpeg")
    assert detection_after["installed"] is True
    assert detection_after["source"] == "local"
    assert detection_after["can_uninstall"] is True

    # Test uninstall
    removed = uninstall_local_capability("ffmpeg")
    assert removed is True
    assert not os.path.exists(fake_ffmpeg)


def test_system_installed_capability_safe(monkeypatch, tmp_path):
    """Ensure system installed binaries cannot be uninstalled via Intra."""
    fake_local = str(tmp_path / "AppDataLocal")
    monkeypatch.setenv("LOCALAPPDATA", fake_local)

    # Mock shutil.which to simulate system-installed tesseract
    monkeypatch.setattr(shutil, "which", lambda cmd: "/usr/bin/tesseract" if "tesseract" in cmd else None)

    detection = detect_capability("tesseract")
    assert detection["installed"] is True
    assert detection["source"] == "system"
    assert detection["can_uninstall"] is False

    # Attempting to uninstall should not touch system files
    uninstall_local_capability("tesseract")
    assert detection["can_uninstall"] is False
