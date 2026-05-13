#!/bin/bash
set -euo pipefail

TANSO_OSS="$(cd "$(dirname "$0")/.." && pwd)"
echo "=== Strip Observe mode (AI insights, analytics, models, sources) ==="

# Backend: remove AI insight files
rm -f "$TANSO_OSS/apps/api/src/main/java/com/tansoflow/tansocore/entity/AiInsight.java"
rm -f "$TANSO_OSS/apps/api/src/main/java/com/tansoflow/tansocore/repository/AiInsightRepository.java"
rm -f "$TANSO_OSS/apps/api/src/main/java/com/tansoflow/tansocore/controller/tanso/analytics/AiInsightController.java"
rm -f "$TANSO_OSS/apps/api/src/main/java/com/tansoflow/tansocore/mapper/analytics/AiInsightMapper.java"
rm -f "$TANSO_OSS/apps/api/src/main/java/com/tansoflow/tansocore/model/analytics/AiInsightDto.java"
rm -f "$TANSO_OSS/apps/api/src/main/java/com/tansoflow/tansocore/model/analytics/AiInsightSeverity.java"
rm -f "$TANSO_OSS/apps/api/src/main/java/com/tansoflow/tansocore/service/internal/analytics/AiInsightService.java"
rm -f "$TANSO_OSS/apps/api/src/main/java/com/tansoflow/tansocore/service/internal/analytics/implementation/AiInsightServiceImpl.java"
rm -f "$TANSO_OSS/apps/api/src/main/java/com/tansoflow/tansocore/service/internal/analytics/implementation/AnalyticsServiceImpl.java"
rm -f "$TANSO_OSS/apps/api/src/main/java/com/tansoflow/tansocore/mcp/tools/AiInsightTools.java"
rm -f "$TANSO_OSS/apps/api/src/main/java/com/tansoflow/tansocore/model/data/stripe/response/StripeObserveSyncResponse.java"
rm -f "$TANSO_OSS/apps/api/src/main/java/com/tansoflow/tansocore/integration/stripe/StripeObserveSyncService.java"
rm -f "$TANSO_OSS/apps/api/src/main/java/com/tansoflow/tansocore/integration/stripe/implementation/StripeObserveSyncServiceImpl.java"
rm -f "$TANSO_OSS/apps/api/src/main/java/com/tansoflow/tansocore/util/ModelProviderResolver.java"

# Backend: remove Observe-related DB migrations
rm -f "$TANSO_OSS/apps/api/src/main/resources/db/changelog/2026.03.25.1.yaml"
rm -f "$TANSO_OSS/apps/api/src/main/resources/db/changelog/2026.03.30.2.yaml"

# Backend: remove Observe tests
rm -f "$TANSO_OSS/apps/api/src/test/java/com/tansoflow/tansocore/controller/tanso/analytics/AiInsightControllerTest.java"
rm -f "$TANSO_OSS/apps/api/src/test/java/com/tansoflow/tansocore/service/internal/analytics/implementation/AiInsightServiceImplTest.java"
rm -f "$TANSO_OSS/apps/api/src/test/java/com/tansoflow/tansocore/util/ModelPricingResolverTest.java"

# Frontend: remove Observe feature directories
rm -rf "$TANSO_OSS/apps/dashboard/src/features/analytics/"
rm -rf "$TANSO_OSS/apps/dashboard/src/features/models/"
rm -rf "$TANSO_OSS/apps/dashboard/src/features/sources/"

echo "=== Observe mode stripped ==="
echo "NOTE: Router (router.ts) still has routes for analytics/models/sources — needs manual removal."
echo "NOTE: pom.xml still has openai dependency — needs manual removal."
echo "NOTE: ExternalClientsConfig.java, EventServiceImpl.java, EventDto.java may reference Observe — check manually."
