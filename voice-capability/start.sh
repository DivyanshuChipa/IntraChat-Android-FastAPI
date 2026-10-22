#!/usr/bin/env bash
# ==============================================================================
# 🚀 Start Voice-Capability Service on Linux (Node 2)
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [ ! -d "venv" ]; then
    echo "⚠️ Virtual environment not found. Running './install.sh' first..."
    ./install.sh
fi

source venv/bin/activate
PORT=${1:-8005}

echo "🎙️ Starting Voice-Capability on Port $PORT..."
python3 voice_server.py --host 0.0.0.0 --port "$PORT"
