package com.tansoflow.tansocore.model.feature;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Data Transfer Object for Feature information")
public class FeatureDto {
    @Schema(description = "Unique identifier of the feature")
    private UUID id;

    @Schema(description = "Display name of the feature", example = "SSO Authentication")
    private String name;

    @Schema(description = "Unique key for the feature", example = "sso_auth")
    private String key;

    @Schema(description = "Detailed description of the feature")
    private String description;

    @Schema(description = "Timestamp when the feature was created")
    private Instant createdAt;

    @Schema(description = "Timestamp when the feature was last modified")
    private Instant modifiedAt;

    @Schema(description = "Status indicating if the feature is enabled")
    private Boolean isEnabled;

    @Schema(description = "Additional metadata associated with the feature")
    private Map<String, Object> metadata;
}
