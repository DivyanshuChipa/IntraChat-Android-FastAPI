#!/usr/bin/env bash
set -euo pipefail

GREEN='\033[0;32m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m'

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR/backend"
VENV_DIR="$BACKEND_DIR/venv"

print_header() {
  echo -e "${GREEN}=======================================${NC}"
  echo -e "${GREEN}   INTRA - BACKEND SERVER (LINUX)      ${NC}"
  echo -e "${GREEN}=======================================${NC}"
}

print_header

if ! command -v python3 >/dev/null 2>&1; then
  echo -e "${RED}❌ Error: Python3 is not installed.${NC}"
  exit 1
fi

# Check for external tools
MISSING_TOOLS=()
for tool in ffmpeg tesseract gs; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    MISSING_TOOLS+=("$tool")
  fi
done

if [ ${#MISSING_TOOLS[@]} -ne 0 ]; then
  echo -e "${RED}⚠️  Missing system tools: ${MISSING_TOOLS[*]}${NC}"
  echo -e "${CYAN}You can install them using:${NC}"
  echo -e "sudo apt update && sudo apt install -y ffmpeg tesseract-ocr tesseract-ocr-hin ghostscript"
  echo ""
  read -p "Do you want to try installing them now? (y/n) " -n 1 -r
  echo
  if [[ $REPLY =~ ^[Yy]$ ]]; then
    sudo apt update && sudo apt install -y ffmpeg tesseract-ocr tesseract-ocr-hin ghostscript
  fi
fi

if [ ! -d "$BACKEND_DIR" ]; then
  echo -e "${RED}❌ Error: backend folder not found at: $BACKEND_DIR${NC}"
  exit 1
fi

cd "$BACKEND_DIR"

if [ ! -d "$VENV_DIR" ]; then
  echo -e "${CYAN}📦 Creating virtual environment...${NC}"
  python3 -m venv "$VENV_DIR"
fi

# shellcheck disable=SC1091
source "$VENV_DIR/bin/activate"

echo -e "${CYAN}🔄 Installing/updating dependencies...${NC}"
python -m pip install --upgrade pip
python -m pip install -r requirements.txt

echo -e "${GREEN}🚀 Starting Intra Backend...${NC}"
python run_server.py "$@"
