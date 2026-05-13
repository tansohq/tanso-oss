#!/bin/bash
set -euo pipefail

TANSO_OSS="$(cd "$(dirname "$0")/.." && pwd)"
echo "=== Strip secrets ==="

# Remove environment-specific YAML profiles (they contain hardcoded keys)
echo "Removing environment-specific YAML profiles..."
rm -f "$TANSO_OSS/apps/api/src/main/resources/application-dev.yaml"
rm -f "$TANSO_OSS/apps/api/src/main/resources/application-prod.yaml"
rm -f "$TANSO_OSS/apps/api/src/main/resources/application-sandbox.yaml"
rm -f "$TANSO_OSS/apps/api/src/main/resources/application-staging.yaml"

# Remove frontend env files with secrets
echo "Removing frontend env files..."
rm -f "$TANSO_OSS/apps/dashboard/.env.production"
rm -f "$TANSO_OSS/apps/dashboard/.env.sandbox"
rm -f "$TANSO_OSS/apps/dashboard/.env.staging"

# Remove master account seed migration (contains hardcoded API key)
echo "Removing master account seed migration..."
rm -f "$TANSO_OSS/apps/api/src/main/resources/db/changelog/2026.02.03.1.yaml"

echo "=== Secrets stripped ==="
