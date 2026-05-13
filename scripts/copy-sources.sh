#!/bin/bash
set -euo pipefail

TANSOFLOW="${1:-$HOME/Desktop/Github-Wiki/GitHub/tansoflow}"
TANSO_OSS="$(cd "$(dirname "$0")/.." && pwd)"

echo "=== Copy sources from $TANSOFLOW to $TANSO_OSS ==="

# Backend: copy tanso-core → apps/api (exclude build artifacts, .git, IDE files)
echo "Copying tanso-core → apps/api..."
rsync -a --delete \
  --exclude='target/' \
  --exclude='.git/' \
  --exclude='.idea/' \
  --exclude='*.iml' \
  --exclude='.DS_Store' \
  "$TANSOFLOW/tanso-core/" "$TANSO_OSS/apps/api/"

# Frontend: copy tanso-dashboard → apps/dashboard (exclude node_modules, dist, .git)
echo "Copying tanso-dashboard → apps/dashboard..."
rsync -a --delete \
  --exclude='node_modules/' \
  --exclude='dist/' \
  --exclude='.git/' \
  --exclude='.idea/' \
  --exclude='.DS_Store' \
  "$TANSOFLOW/tanso-dashboard/" "$TANSO_OSS/apps/dashboard/"

# SDK: copy sdks/java → packages/ if exists
if [ -d "$TANSOFLOW/sdks/java" ]; then
  echo "Copying sdks/java..."
  rsync -a --exclude='.git/' --exclude='target/' \
    "$TANSOFLOW/sdks/java/" "$TANSO_OSS/packages/sdk-java/"
fi

# Docs: copy docs/ content
echo "Copying docs..."
rsync -a --exclude='.git/' \
  "$TANSOFLOW/docs/" "$TANSO_OSS/docs/"

echo "=== Copy complete ==="
