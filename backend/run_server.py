import argparse
import socket
import subprocess
import sys


def get_ip() -> str:
    """Return local LAN IP when available, otherwise fallback to localhost."""
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        sock.connect(("8.8.8.8", 1))
        ip_addr = sock.getsockname()[0]
    except Exception:
        ip_addr = "127.0.0.1"
    finally:
        sock.close()
    return ip_addr


def ensure_dependencies() -> None:
    """Install requirements only when uvicorn is missing."""
    try:
        import uvicorn  # noqa: F401
    except ImportError:
        print("📦 Installing missing libraries... please wait.")
        subprocess.check_call([sys.executable, "-m", "pip", "install", "-r", "requirements.txt"])
        print("✅ Libraries installed successfully!\n")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Intra backend server starter")
    parser.add_argument("--host", default="0.0.0.0", help="Host to bind (default: 0.0.0.0)")
    parser.add_argument("--port", type=int, default=8000, help="Port to bind (default: 8000)")
    parser.add_argument(
        "--reload",
        dest="reload",
        action="store_true",
        default=True,
        help="Enable auto-reload (default: enabled)",
    )
    parser.add_argument(
        "--no-reload",
        dest="reload",
        action="store_false",
        help="Disable auto-reload (recommended for systemd/production)",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()

    print("=======================================")
    print("   INTRA - BACKEND SERVER STARTER      ")
    print("=======================================")

    local_ip = get_ip()
    print(f"\n🚀 Server starting on: http://{local_ip}:{args.port}")
    print(f"📱 Android App me ye IP daalein: {local_ip}")
    print("---------------------------------------")

    ensure_dependencies()

    import uvicorn

    uvicorn.run("server:app", host=args.host, port=args.port, reload=args.reload)


if __name__ == "__main__":
    main()
