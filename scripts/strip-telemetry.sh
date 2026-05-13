#!/bin/bash
set -euo pipefail

TANSO_OSS="$(cd "$(dirname "$0")/.." && pwd)"
echo "=== Strip telemetry (PostHog, Userback) ==="

# Remove PostHog
rm -f "$TANSO_OSS/apps/dashboard/src/lib/posthog.ts"
rm -f "$TANSO_OSS/apps/dashboard/src/lib/tracking.ts"

# Remove Userback
rm -f "$TANSO_OSS/apps/dashboard/src/lib/userback.ts"

echo "=== Telemetry stripped ==="
echo "NOTE: main.ts and auth store still import posthog/userback — needs manual cleanup."
