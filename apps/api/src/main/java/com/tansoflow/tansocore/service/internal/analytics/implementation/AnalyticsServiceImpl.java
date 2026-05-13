package com.tansoflow.tansocore.service.internal.analytics.implementation;

import com.tansoflow.tansocore.model.analytics.AnalyticsResponseDto;
import com.tansoflow.tansocore.model.analytics.ModelsAnalyticsResponseDto;
import com.tansoflow.tansocore.model.analytics.RevenueBridgeResponseDto;
import com.tansoflow.tansocore.service.internal.analytics.AnalyticsService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    @Override
    public AnalyticsResponseDto getPortfolioAnalytics(String accountId) {
        return AnalyticsResponseDto.builder().customers(List.of()).build();
    }

    @Override
    public RevenueBridgeResponseDto getRevenueBridge(String accountId, int periods) {
        return RevenueBridgeResponseDto.builder().periods(List.of()).build();
    }

    @Override
    public ModelsAnalyticsResponseDto getModelAnalytics(String accountId) {
        return ModelsAnalyticsResponseDto.builder().models(List.of()).build();
    }
}
