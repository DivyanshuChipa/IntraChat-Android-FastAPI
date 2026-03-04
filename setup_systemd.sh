#!/bin/bash

# Get current folder path (Root)
ROOT_DIR=$(pwd)
BACKEND_DIR="$ROOT_DIR/backend"
USER=$(whoami)

# Systemd file creation
SERVICE_FILE="/etc/systemd/system/intra_backend.service"

echo "======================================="
echo "   INTRA SYSTEMD SERVICE SETUP         "
echo "======================================="

# Checking for root
if [ "$EUID" -ne 0 ]
  then echo "❌ Please run as root (sudo ./setup_systemd.sh)"
  exit
fi

# Check if backend exists
if [ ! -d "$BACKEND_DIR" ]; then
    echo "❌ Error: backend folder not found at $BACKEND_DIR"
    exit 1
fi

# Write Service File
cat > $SERVICE_FILE <<EOF
[Unit]
Description=Intra Backend Server Service
After=network.target

[Service]
User=$USER
WorkingDirectory=$BACKEND_DIR
ExecStart=$BACKEND_DIR/venv/bin/python3 $BACKEND_DIR/run_server.py
Restart=always
RestartSec=3
Environment=PATH=$BACKEND_DIR/venv/bin:/usr/bin:/usr/local/bin

[Install]
WantedBy=multi-user.target
EOF

# Enable and start
systemctl daemon-reload
systemctl enable intra_backend.service
systemctl start intra_backend.service

echo "✅ Intra Backend service is now active and enabled!"
echo "Check status with: sudo systemctl status intra_backend"
