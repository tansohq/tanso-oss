package com.tansoflow.tansocore.model.monetization.response;

import com.tansoflow.tansocore.model.feature.FeatureDto;
import lombok.Data;

import java.util.List;

@Data
public class PlanFeatureLinkedDiffResponse {
    List<FeatureDto> addedFeatures;
    List<FeatureDto> removedFeatures;
}
