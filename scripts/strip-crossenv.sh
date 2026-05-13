#!/bin/bash
set -euo pipefail

TANSO_OSS="$(cd "$(dirname "$0")/.." && pwd)"
echo "=== Strip cross-env replication ==="

# Remove CrossEnvProperty config class
rm -f "$TANSO_OSS/apps/api/src/main/java/com/tansoflow/tansocore/property/CrossEnvProperty.java"

echo "=== Cross-env stripped ==="
echo "NOTE: OnboardingService and OnboardingController still reference cross-env."
echo "These need manual editing to remove replication methods while keeping signup logic."
