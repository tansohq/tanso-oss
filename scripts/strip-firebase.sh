#!/bin/bash
set -euo pipefail

TANSO_OSS="$(cd "$(dirname "$0")/.." && pwd)"
echo "=== Strip Firebase ==="

rm -rf "$TANSO_OSS/apps/api/src/main/java/com/tansoflow/tansocore/integration/firebase/"

echo "=== Firebase stripped ==="
echo "NOTE: pom.xml still has firebase-admin dependency — needs manual removal."
