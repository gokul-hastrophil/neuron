#!/bin/bash
# Neuron Daily Startup — Run at the start of each work session
# This script triggers the daily startup agent which checks for updates,
# reviews project status, and prepares the environment.
#
# Usage:
#   ./scripts/daily_startup.sh
#
# Note: Make executable with:
#   chmod +x /home/disk/Personal/projects/neuron/neuron/scripts/daily_startup.sh

set -euo pipefail

# Get the project root directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

echo "=========================================="
echo "Neuron Daily Startup"
echo "=========================================="
echo "Date: $(date '+%Y-%m-%d %H:%M:%S')"
echo "Project: $PROJECT_ROOT"
echo ""

echo "Checking environment..."
if command -v claude &> /dev/null; then
    echo "✓ Claude CLI found"
else
    echo "✗ Claude CLI not found. Install with: pip install anthropic"
    exit 1
fi

echo ""
echo "Executing daily startup checklist..."
echo "This includes:"
echo "  • Checking for updates to key technologies"
echo "  • Reviewing component status"
echo "  • Updating tech radar"
echo "  • Suggesting next steps"
echo ""

# Run the daily startup agent
# This agent will:
# 1. Search for updates to Android AccessibilityService, Gemma 3n, Gemini API, Claude API, MCP, Porcupine, Jetpack Compose, Hilt, FastAPI
# 2. Check dependency versions in the project
# 3. Update memory/knowledge/tech_radar.md
# 4. Update memory/reliability/scores.json if needed
# 5. Provide a summary and suggestions

claude_prompt=$(cat <<'EOF'
Execute the daily startup checklist for the Neuron project:

1. Search for updates to these technologies:
   - Android AccessibilityService (latest API changes)
   - Gemma 3n (on-device model updates)
   - Gemini API (new models, updates)
   - Anthropic Claude API (latest models, features)
   - MCP specification (Model Context Protocol)
   - Porcupine (wake word detection)
   - Jetpack Compose (latest stable releases)
   - Hilt (dependency injection)
   - FastAPI (Python web framework)

2. Check current dependency versions in the project (build.gradle, requirements.txt, etc.)

3. Update memory/knowledge/tech_radar.md with findings:
   - New releases or major updates
   - Deprecations or breaking changes
   - Security updates
   - Performance improvements
   - Recommended actions

4. Review memory/reliability/scores.json and update if any component status has changed

5. Provide a summary including:
   - What's new in the ecosystem
   - Any critical security updates
   - Recommended actions or upgrades
   - Suggested focus areas for development

Format your response clearly with sections for each technology area.
EOF
)

echo "$claude_prompt" | claude --print

echo ""
echo "=========================================="
echo "Daily startup complete!"
echo "=========================================="
echo ""
echo "Tip: Check memory/knowledge/tech_radar.md for detailed updates"
echo ""
