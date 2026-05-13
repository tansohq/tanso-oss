package com.tansoflow.tansocore.model.monetization.request;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class PlanFeatureLinkedDiffRequest {
    List<LinkFeature> features;

    @Data
    public static class LinkFeature {
        private UUID featureId;
        private Boolean isEnabled;
        private String type;
        private Map<String, Object> value;
        private UUID creditModelId;
    }
}
