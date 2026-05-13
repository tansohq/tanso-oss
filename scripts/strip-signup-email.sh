#!/bin/bash
set -euo pipefail

TANSO_OSS="$(cd "$(dirname "$0")/.." && pwd)"
echo "=== Strip signup email system ==="

# Remove signup email drip system
rm -f "$TANSO_OSS/apps/api/src/main/java/com/tansoflow/tansocore/entity/SignupEmailTracker.java"
rm -f "$TANSO_OSS/apps/api/src/main/java/com/tansoflow/tansocore/repository/SignupEmailTrackerRepository.java"
rm -f "$TANSO_OSS/apps/api/src/main/java/com/tansoflow/tansocore/jobs/scheduler/email/SignupEmailJob.java"
rm -f "$TANSO_OSS/apps/api/src/main/java/com/tansoflow/tansocore/service/internal/email/SignupEmailService.java"

# Remove notify upgrade controller
rm -f "$TANSO_OSS/apps/api/src/main/java/com/tansoflow/tansocore/controller/tanso/account/NotifyUpgradeController.java"

echo "=== Signup email stripped ==="
echo "NOTE: AccountController.java still has hardcoded email addresses (kat@/doug@tansohq.com)."
echo "This needs manual editing to make email addresses configurable."
