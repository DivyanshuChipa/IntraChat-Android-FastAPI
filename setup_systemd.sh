#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="intra_backend.service"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR/backend"
SERVICE_FILE="/etc/systemd/system/$SERVICE_NAME"
RUN_USER="${SUDO_USER:-$(whoami)}"

usage() {
  cat <<USAGE
Usage:
  sudo ./setup_systemd.sh install   # install + enable + start service
  sudo ./setup_systemd.sh uninstall # stop + disable + remove service
USAGE
}

check_root() {
  if [ "$EUID" -ne 0 ]; then
    echo "❌ Please run as root: sudo ./setup_systemd.sh <install|uninstall>"
    exit 1
  fi
}

install_service() {
  if [ ! -d "$BACKEND_DIR" ]; then
    echo "❌ Error: backend folder not found at $BACKEND_DIR"
    exit 1
  fi

  if ! command -v python3 >/dev/null 2>&1; then
    echo "❌ python3 not found. Install Python 3 first."
    exit 1
  fi

  if [ ! -d "$BACKEND_DIR/venv" ]; then
    echo "📦 Creating virtual environment..."
    python3 -m venv "$BACKEND_DIR/venv"
  fi

  echo "🔄 Installing dependencies..."
  "$BACKEND_DIR/venv/bin/python" -m pip install --upgrade pip
  "$BACKEND_DIR/venv/bin/python" -m pip install -r "$BACKEND_DIR/requirements.txt"

  cat > "$SERVICE_FILE" <<EOF_SERVICE
[Unit]
Description=Intra Backend Server Service
After=network.target

[Service]
Type=simple
User=$RUN_USER
WorkingDirectory=$BACKEND_DIR
ExecStart=$BACKEND_DIR/venv/bin/python $BACKEND_DIR/run_server.py --host 0.0.0.0 --port 8000
Restart=always
RestartSec=3
Environment=PYTHONUNBUFFERED=1

[Install]
WantedBy=multi-user.target
EOF_SERVICE

  systemctl daemon-reload
  systemctl enable "$SERVICE_NAME"
  systemctl restart "$SERVICE_NAME"

  echo "✅ Service installed and running."
  echo "Status: sudo systemctl status $SERVICE_NAME"
}

uninstall_service() {
  systemctl stop "$SERVICE_NAME" 2>/dev/null || true
  systemctl disable "$SERVICE_NAME" 2>/dev/null || true

  if [ -f "$SERVICE_FILE" ]; then
    rm -f "$SERVICE_FILE"
    echo "🗑️ Removed $SERVICE_FILE"
  else
    echo "ℹ️ Service file not found, skipping remove"
  fi

  systemctl daemon-reload
  systemctl reset-failed
  echo "✅ Service uninstalled."
}

ACTION="${1:-install}"
check_root

case "$ACTION" in
  install)
    install_service
    ;;
  uninstall|remove)
    uninstall_service
    ;;
  *)
    usage
    exit 1
    ;;
esac
