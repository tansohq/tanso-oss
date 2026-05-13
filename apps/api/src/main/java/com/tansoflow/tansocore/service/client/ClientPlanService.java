package com.tansoflow.tansocore.service.client;

import com.tansoflow.tansocore.model.client.ClientPlanFeatureLinkedDto;

import java.util.List;

public interface ClientPlanService {
    List<ClientPlanFeatureLinkedDto> retrieveActivePlansWithPricing(String accountId);
}
