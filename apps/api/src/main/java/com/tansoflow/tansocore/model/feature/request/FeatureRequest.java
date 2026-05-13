package com.tansoflow.tansocore.model.feature.request;

import lombok.Data;

import java.util.Map;

@Data
public class FeatureRequest {
    private String name;
    private String key;
    private String description;
    private Boolean isEnabled;
    private Map<String, Object> metadata;
}
