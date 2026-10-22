#!/usr/bin/env bash
# ==============================================================================
# 🎙️ Install Voice-Capability on Linux (Node 2 - i3 12th Gen)
# ==============================================================================

set -e

echo "=========================================================="
echo "  🚀 Installing Voice-Capability on Linux (Node 2)"
echo "=========================================================="

# 1. System packages (espeak-ng as fallback & ffmpeg for audio decode)
echo "📦 Installing system dependencies (espeak-ng, ffmpeg, python3-venv)..."
if command -v apt-get &> /dev/null; then
    sudo apt-get update
    sudo apt-get install -y python3-pip python3-venv espeak-ng ffmpeg
elif command -v dnf &> /dev/null; then
    sudo dnf install -y python3-pip espeak-ng ffmpeg
elif command -v pacman &> /dev/null; then
    sudo pacman -S --noconfirm python-pip espeak-ng ffmpeg
fi

# 2. Python Virtual Environment
echo "🐍 Setting up Python Virtual Environment..."
if [ ! -d "venv" ]; then
    python3 -m venv venv
fi

source venv/bin/activate
pip install --upgrade pip

# 3. Pip Requirements
echo "📥 Installing Python requirements..."
pip install -r requirements.txt

# 4. Optional Piper binary download if not available
if ! command -v piper &> /dev/null; then
    echo "💡 Checking for Piper TTS binary..."
    # piper-tts python package is installed via requirements.txt
fi

echo ""
echo "=========================================================="
echo "  ✅ Voice-Capability installed successfully on Node 2!"
echo "  👉 Run './start.sh' to start the service on port 8005"
echo "=========================================================="
