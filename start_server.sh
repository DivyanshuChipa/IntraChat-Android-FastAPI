#!/bin/bash

# ANSI color codes
GREEN='\033[0;32m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}=======================================${NC}"
echo -e "${GREEN}   INTRA - BACKEND SERVER (LINUX)      ${NC}"
echo -e "${GREEN}=======================================${NC}"

# Go to backend folder
cd backend || { echo -e "${RED}❌ Error: backend folder not found!${NC}"; exit 1; }

# 1. Check Python
if ! command -v python3 &> /dev/null; then
    echo -e "${RED}❌ Error: Python3 is not installed.${NC}"
    exit 1
fi

# 2. Setup Virtual Environment (venv)
if [ ! -d "venv" ]; then
    echo -e "${CYAN}📦 Creating Virtual Environment...${NC}"
    python3 -m venv venv
fi

# 3. Activate venv and Install Dependencies
echo -e "${CYAN}🔄 Syncing dependencies...${NC}"
source venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt

# 4. Run Server
echo -e "${GREEN}🚀 Starting Intra Backend...${NC}"
python3 run_server.py
