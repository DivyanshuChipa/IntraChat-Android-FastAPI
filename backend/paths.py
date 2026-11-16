"""
backend/paths.py - Centralized Data & Storage Path Resolver for Intra Server.
Ensures zero permission errors on Windows by routing all mutable user data
(SQLite databases, ChromaDB vector stores, uploads, and temporary files)
to %LOCALAPPDATA%/IntraServer/ when running in production or installed in Program Files.
Also handles graceful auto-migration from legacy local backend files.
"""

import os
import sys
import shutil


def get_intra_data_dir() -> str:
    """Returns the root directory for all mutable user data."""
    if "INTRA_DATA_DIR" in os.environ:
        path = os.path.abspath(os.environ["INTRA_DATA_DIR"])
        os.makedirs(path, exist_ok=True)
        return path

    curr_dir = os.path.abspath(os.path.dirname(__file__))
    is_program_files = (
        "program files" in curr_dir.lower() or "\\dist\\" in curr_dir.lower()
    )
    has_git = os.path.exists(os.path.join(curr_dir, "..", ".git")) or os.path.exists(
        os.path.join(curr_dir, ".git")
    )
    is_installed = is_program_files or not has_git or os.environ.get("INTRA_FORCE_APPDATA") == "1"

    # On Windows production/installed mode, always use %LOCALAPPDATA%/IntraServer
    if sys.platform == "win32" and is_installed and os.environ.get("INTRA_DEV_MODE") != "1":
        local_app = os.environ.get("LOCALAPPDATA")
        if not local_app:
            local_app = os.path.expanduser("~\\AppData\\Local")
        target = os.path.join(local_app, "IntraServer")
        os.makedirs(target, exist_ok=True)
        return target

    # Local development fallback (in repo directory)
    return curr_dir


def _migrate_file_if_needed(legacy_path: str, target_path: str):
    """
    If target_path does not exist, but legacy_path exists in the source tree,
    safely copy it over to preserve existing user data without permission errors.
    """
    try:
        if not os.path.exists(target_path) and os.path.exists(legacy_path):
            os.makedirs(os.path.dirname(target_path), exist_ok=True)
            shutil.copy2(legacy_path, target_path)
            print(f"📦 [STORAGE MIGRATION] Migrated '{legacy_path}' -> '{target_path}'")
    except Exception as e:
        print(f"⚠️ [STORAGE MIGRATION WARNING] Could not copy {legacy_path} to {target_path}: {e}")


def get_db_path() -> str:
    """Returns absolute path to chat_users.db."""
    if "INTRA_DB_PATH" in os.environ:
        return os.environ["INTRA_DB_PATH"]

    data_dir = get_intra_data_dir()
    curr_backend = os.path.abspath(os.path.dirname(__file__))
    legacy_db = os.path.join(curr_backend, "chat_users.db")

    if data_dir == curr_backend:
        return legacy_db

    target_dir = os.path.join(data_dir, "data")
    os.makedirs(target_dir, exist_ok=True)
    target_db = os.path.join(target_dir, "chat_users.db")

    _migrate_file_if_needed(legacy_db, target_db)
    return target_db


def get_messages_db_path() -> str:
    """Returns absolute path to chat_messages.db."""
    if "INTRA_MSG_DB_PATH" in os.environ:
        return os.environ["INTRA_MSG_DB_PATH"]

    data_dir = get_intra_data_dir()
    curr_backend = os.path.abspath(os.path.dirname(__file__))
    legacy_db = os.path.join(curr_backend, "chat_messages.db")

    if data_dir == curr_backend:
        return legacy_db

    target_dir = os.path.join(data_dir, "data")
    os.makedirs(target_dir, exist_ok=True)
    target_db = os.path.join(target_dir, "chat_messages.db")

    _migrate_file_if_needed(legacy_db, target_db)
    return target_db


def get_sqlite_brain_path() -> str:
    """Returns absolute path to lumir_brain.db for fallback neuro-storage."""
    if "INTRA_BRAIN_DB_PATH" in os.environ:
        return os.environ["INTRA_BRAIN_DB_PATH"]

    data_dir = get_intra_data_dir()
    curr_backend = os.path.abspath(os.path.dirname(__file__))
    legacy_db = os.path.join(curr_backend, "lumir", "lumir_brain.db")

    if data_dir == curr_backend:
        return legacy_db

    target_dir = os.path.join(data_dir, "data")
    os.makedirs(target_dir, exist_ok=True)
    target_db = os.path.join(target_dir, "lumir_brain.db")

    _migrate_file_if_needed(legacy_db, target_db)
    return target_db


def get_uploads_dir() -> str:
    """Returns absolute path to uploads/ directory."""
    if "INTRA_UPLOADS_DIR" in os.environ:
        path = os.environ["INTRA_UPLOADS_DIR"]
        os.makedirs(path, exist_ok=True)
        return path

    data_dir = get_intra_data_dir()
    uploads_dir = os.path.join(data_dir, "uploads")
    os.makedirs(uploads_dir, exist_ok=True)
    return uploads_dir


def get_chroma_dir() -> str:
    """Returns absolute path to chroma_storage directory for vector memory."""
    if "INTRA_CHROMA_DIR" in os.environ:
        path = os.environ["INTRA_CHROMA_DIR"]
        os.makedirs(path, exist_ok=True)
        return path

    data_dir = get_intra_data_dir()
    curr_backend = os.path.abspath(os.path.dirname(__file__))

    # In dev mode, keep it inside lumir/chroma_storage
    if data_dir == curr_backend:
        chroma_dir = os.path.join(data_dir, "lumir", "chroma_storage")
    else:
        chroma_dir = os.path.join(data_dir, "data", "chroma_storage")

    os.makedirs(chroma_dir, exist_ok=True)
    return chroma_dir


def get_cache_dir() -> str:
    """Returns absolute path to cache directory for weather & alert trackers."""
    if "INTRA_CACHE_DIR" in os.environ:
        path = os.environ["INTRA_CACHE_DIR"]
        os.makedirs(path, exist_ok=True)
        return path

    data_dir = get_intra_data_dir()
    curr_backend = os.path.abspath(os.path.dirname(__file__))

    if data_dir == curr_backend:
        cache_dir = curr_backend
    else:
        cache_dir = os.path.join(data_dir, "cache")

    os.makedirs(cache_dir, exist_ok=True)
    return cache_dir
