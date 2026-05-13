package com.tansoflow.tansocore.service.internal.analytics;

import com.tansoflow.tansocore.model.analytics.AnalyticsResponseDto;
import com.tansoflow.tansocore.model.analytics.ModelsAnalyticsResponseDto;
import com.tansoflow.tansocore.model.analytics.RevenueBridgeResponseDto;

public interface AnalyticsService {
    AnalyticsResponseDto getPortfolioAnalytics(String accountId);
    RevenueBridgeResponseDto getRevenueBridge(String accountId, int periods);
    ModelsAnalyticsResponseDto getModelAnalytics(String accountId);
}
