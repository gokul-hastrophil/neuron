#!/bin/bash
# ============================================================
# NEURON PROJECT — Linux Development Environment Setup
# Run once on a fresh Ubuntu/Debian machine
# Usage: chmod +x setup_linux.sh && ./setup_linux.sh
# ============================================================

set -e  # Exit on any error

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

NEURON_DIR="$HOME/neuron"
ANDROID_SDK_DIR="$HOME/Android/Sdk"
ANDROID_SDK_VERSION="commandlinetools-linux-11076708_latest.zip"
PYTHON_VERSION="3.12"

print_step() { echo -e "\n${PURPLE}▶ $1${NC}"; }
print_ok()   { echo -e "${GREEN}✅ $1${NC}"; }
print_warn() { echo -e "${YELLOW}⚠️  $1${NC}"; }
print_info() { echo -e "${CYAN}ℹ️  $1${NC}"; }

echo -e "${BLUE}"
cat << 'EOF'
 ███╗   ██╗███████╗██╗   ██╗██████╗  ██████╗ ███╗   ██╗
 ████╗  ██║██╔════╝██║   ██║██╔══██╗██╔═══██╗████╗  ██║
 ██╔██╗ ██║█████╗  ██║   ██║██████╔╝██║   ██║██╔██╗ ██║
 ██║╚██╗██║██╔══╝  ██║   ██║██╔══██╗██║   ██║██║╚██╗██║
 ██║ ╚████║███████╗╚██████╔╝██║  ██║╚██████╔╝██║ ╚████║
 ╚═╝  ╚═══╝╚══════╝ ╚═════╝ ╚═╝  ╚═╝ ╚═════╝ ╚═╝  ╚═══╝
 Android AI Agent — Linux Dev Environment Setup v1.0
EOF
echo -e "${NC}"

# ── 1. SYSTEM PACKAGES ──────────────────────────────────────
print_step "Installing system packages"
sudo apt-get update -qq
sudo apt-get install -y \
    git curl wget unzip zip \
    build-essential pkg-config \
    libssl-dev libffi-dev \
    openjdk-17-jdk \
    adb \
    python3 python3-pip python3-venv python3-dev \
    docker.io docker-compose-v2 \
    nodejs npm \
    jq yq \
    htop tree ncdu \
    vim neovim \
    2>/dev/null
print_ok "System packages installed"

# Java 17 as default
sudo update-alternatives --set java /usr/lib/jvm/java-17-openjdk-amd64/bin/java 2>/dev/null || true
print_ok "Java 17 set as default"

# ── 2. ANDROID SDK ──────────────────────────────────────────
print_step "Installing Android SDK"
mkdir -p "$ANDROID_SDK_DIR/cmdline-tools"

if [ ! -f "$ANDROID_SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
    cd /tmp
    wget -q "https://dl.google.com/android/repository/$ANDROID_SDK_VERSION" -O cmdline-tools.zip
    unzip -q cmdline-tools.zip -d "$ANDROID_SDK_DIR/cmdline-tools"
    mv "$ANDROID_SDK_DIR/cmdline-tools/cmdline-tools" "$ANDROID_SDK_DIR/cmdline-tools/latest"
    rm cmdline-tools.zip
    print_ok "Android command line tools downloaded"
else
    print_info "Android SDK already present, skipping download"
fi

export ANDROID_SDK_ROOT="$ANDROID_SDK_DIR"
export PATH="$ANDROID_SDK_DIR/cmdline-tools/latest/bin:$ANDROID_SDK_DIR/platform-tools:$PATH"

yes | sdkmanager --licenses > /dev/null 2>&1 || true
sdkmanager --install \
    "platforms;android-35" \
    "platforms;android-34" \
    "build-tools;35.0.0" \
    "build-tools;34.0.0" \
    "platform-tools" \
    "emulator" \
    "system-images;android-34;google_apis;x86_64" \
    2>/dev/null
print_ok "Android SDK installed (API 34 + 35)"

# ── 3. PYTHON ENVIRONMENT ───────────────────────────────────
print_step "Setting up Python environment"
cd "$NEURON_DIR" 2>/dev/null || cd "$HOME"

python3 -m venv .venv 2>/dev/null || python3.12 -m venv .venv 2>/dev/null || true

if [ -f ".venv/bin/activate" ]; then
    source .venv/bin/activate
    print_ok "Virtual environment created at .venv"
else
    print_warn "Could not create venv, using system Python"
fi

pip install --quiet --upgrade pip
pip install --quiet \
    anthropic \
    google-generativeai \
    openai \
    fastapi \
    uvicorn[standard] \
    httpx \
    websockets \
    mcp \
    pydantic \
    python-dotenv \
    numpy \
    pillow \
    aiofiles \
    rich \
    typer \
    pytest \
    pytest-asyncio \
    ruff \
    black \
    2>/dev/null

print_ok "Python packages installed"

# ── 4. NODE.JS / SDK TOOLING ────────────────────────────────
print_step "Installing Node.js packages"
npm install -g \
    @anthropic-ai/sdk \
    typescript \
    tsx \
    2>/dev/null
print_ok "Node.js packages installed"

# ── 5. CLAUDE CODE ──────────────────────────────────────────
print_step "Installing Claude Code"
if ! command -v claude &> /dev/null; then
    npm install -g @anthropic-ai/claude-code 2>/dev/null || \
    pip install claude-code 2>/dev/null || \
    print_warn "Claude Code install failed — install manually from docs.anthropic.com/claude-code"
else
    print_ok "Claude Code already installed: $(claude --version 2>/dev/null || echo 'unknown')"
fi

# ── 6. ANDROID EMULATOR ─────────────────────────────────────
print_step "Creating Android emulator (Pixel 7 API 34)"
echo "no" | avdmanager create avd \
    --name "Neuron_Pixel7" \
    --package "system-images;android-34;google_apis;x86_64" \
    --device "pixel_7" \
    --force \
    2>/dev/null || print_warn "Emulator creation failed (may need GUI or KVM)"
print_ok "Emulator profile created: Neuron_Pixel7"

# ── 7. DOCKER SETUP ─────────────────────────────────────────
print_step "Setting up Docker"
sudo usermod -aG docker "$USER" 2>/dev/null || true
sudo systemctl enable docker 2>/dev/null || true
sudo systemctl start docker 2>/dev/null || true
print_ok "Docker configured (re-login to use without sudo)"

# ── 8. ENVIRONMENT FILE ─────────────────────────────────────
print_step "Creating .env file"
if [ ! -f "$HOME/neuron/.env" ]; then
    cat > "$HOME/neuron/.env" << 'ENVFILE'
# ====== NEURON ENVIRONMENT CONFIG ======
# Copy to neuron/.env and fill in your values

# LLM API Keys (required for cloud brain)
ANTHROPIC_API_KEY=sk-ant-your-key-here
GEMINI_API_KEY=your-gemini-key-here
OPENAI_API_KEY=sk-your-openai-key-here  # optional

# Neuron Server
NEURON_SERVER_HOST=0.0.0.0
NEURON_SERVER_PORT=8384
NEURON_MCP_PORT=7384
NEURON_SECRET_TOKEN=  # leave empty to auto-generate on first run

# Android Device (for ADB bridge)
ADB_DEVICE_ID=  # leave empty for auto-detect
ANDROID_SDK_ROOT=~/Android/Sdk

# Database paths
NEURON_DB_PATH=~/.neuron/memory.db
NEURON_VECTOR_DB_PATH=~/.neuron/vectors.db

# Development
DEBUG=true
LOG_LEVEL=DEBUG
ENVFILE
    print_ok ".env file created at ~/neuron/.env"
else
    print_info ".env already exists, skipping"
fi

# ── 9. SHELL ALIASES ────────────────────────────────────────
print_step "Installing shell aliases"
ALIAS_BLOCK='
# ====== NEURON DEV ALIASES ======
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

alias neuron="cd ~/neuron && source .venv/bin/activate"
alias neuron-claude="cd ~/neuron && claude"
alias neuron-server="cd ~/neuron && source .venv/bin/activate && python server/mcp/neuron_mcp_server.py"
alias neuron-emulator="$HOME/Android/Sdk/emulator/emulator -avd Neuron_Pixel7 -no-snapshot-load &"
alias neuron-logcat="adb logcat -s NeuronAS:D NeuronBrain:D NeuronMemory:D NeuronOverlay:D"
alias neuron-build="cd ~/neuron/android && ./gradlew assembleDebug"
alias neuron-install="cd ~/neuron/android && ./gradlew installDebug"
alias neuron-test="cd ~/neuron/android && ./gradlew test"
alias neuron-clean="cd ~/neuron/android && ./gradlew clean"
alias neuron-lint="cd ~/neuron && ruff check server/ && cd android && ./gradlew lint"
alias neuron-adb-tree="adb shell uiautomator dump && adb pull /sdcard/window_dump.xml /tmp/ && cat /tmp/window_dump.xml | python3 -m json.tool 2>/dev/null || cat /tmp/window_dump.xml"
alias adb-screenshot="adb exec-out screencap -p > /tmp/phone_screen.png && xdg-open /tmp/phone_screen.png"
# ================================
'

if ! grep -q "NEURON DEV ALIASES" "$HOME/.bashrc" 2>/dev/null; then
    echo "$ALIAS_BLOCK" >> "$HOME/.bashrc"
    print_ok "Aliases added to ~/.bashrc"
fi

if [ -f "$HOME/.zshrc" ] && ! grep -q "NEURON DEV ALIASES" "$HOME/.zshrc" 2>/dev/null; then
    echo "$ALIAS_BLOCK" >> "$HOME/.zshrc"
    print_ok "Aliases added to ~/.zshrc"
fi

# ── 10. GIT CONFIG ──────────────────────────────────────────
print_step "Configuring Git"
git config --global init.defaultBranch main 2>/dev/null || true
git config --global pull.rebase false 2>/dev/null || true

# Install git hooks helper
if [ -d "$HOME/neuron/.git" ]; then
    cp "$HOME/neuron/scripts/pre-commit" "$HOME/neuron/.git/hooks/" 2>/dev/null || true
    chmod +x "$HOME/neuron/.git/hooks/pre-commit" 2>/dev/null || true
fi
print_ok "Git configured"

# ── 11. VS CODE EXTENSIONS (optional) ──────────────────────
if command -v code &> /dev/null; then
    print_step "Installing VS Code extensions"
    code --install-extension ms-python.python --force 2>/dev/null || true
    code --install-extension fwcd.kotlin --force 2>/dev/null || true
    code --install-extension ms-azuretools.vscode-docker --force 2>/dev/null || true
    code --install-extension github.copilot --force 2>/dev/null || true
    print_ok "VS Code extensions installed"
fi

# ── SUMMARY ─────────────────────────────────────────────────
echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║     NEURON DEV ENVIRONMENT READY! 🚀             ║${NC}"
echo -e "${GREEN}╠══════════════════════════════════════════════════╣${NC}"
echo -e "${GREEN}║  Java 17:     $(java -version 2>&1 | head -1 | grep -o '".*"' | tr -d '"')${NC}"
echo -e "${GREEN}║  Android SDK: $ANDROID_SDK_DIR                   ║${NC}"
echo -e "${GREEN}║  Python:      $(python3 --version 2>/dev/null)   ║${NC}"
echo -e "${GREEN}║  Node:        $(node --version 2>/dev/null)                           ║${NC}"
echo -e "${GREEN}║  Docker:      $(docker --version 2>/dev/null | cut -d' ' -f3 | tr -d ',')                         ║${NC}"
echo -e "${GREEN}╠══════════════════════════════════════════════════╣${NC}"
echo -e "${GREEN}║  NEXT STEPS:                                     ║${NC}"
echo -e "${GREEN}║  1. source ~/.bashrc  (reload aliases)           ║${NC}"
echo -e "${GREEN}║  2. Fill in ~/neuron/.env with your API keys     ║${NC}"
echo -e "${GREEN}║  3. Run: neuron-emulator  (start emulator)       ║${NC}"
echo -e "${GREEN}║  4. Run: neuron-build     (build APK)            ║${NC}"
echo -e "${GREEN}║  5. Run: neuron-claude    (start Claude Code)    ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════╝${NC}"
