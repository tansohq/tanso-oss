#!/bin/bash
set -euo pipefail

TANSO_OSS="$(cd "$(dirname "$0")/.." && pwd)"
echo "=== Strip internal/infra files ==="

# Remove .claude directories (agent memory)
find "$TANSO_OSS/apps" -name ".claude" -type d -exec rm -rf {} + 2>/dev/null || true
find "$TANSO_OSS/apps" -name ".cursor" -type d -exec rm -rf {} + 2>/dev/null || true

# Remove CLAUDE.md files from apps (these are internal)
find "$TANSO_OSS/apps" -name "CLAUDE.md" -delete 2>/dev/null || true

# Remove internal files that got copied from root
rm -f "$TANSO_OSS/apps/api/findings.md" 2>/dev/null || true
rm -f "$TANSO_OSS/apps/api/progress.md" 2>/dev/null || true

echo "=== Internal files stripped ==="
