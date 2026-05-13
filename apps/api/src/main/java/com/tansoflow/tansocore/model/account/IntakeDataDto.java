package com.tansoflow.tansocore.model.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Onboarding intake data collected during account setup")
public class IntakeDataDto {

    @NotNull(message = "Role is required")
    @Schema(description = "The user's role", example = "ENGINEER")
    private Role role;

    @Schema(description = "Custom role text when role is OTHER")
    private String roleOther;

    @NotNull(message = "Building type is required")
    @Schema(description = "What the user is building", example = "AI_APP")
    private BuildingType buildingType;

    @Schema(description = "Custom building type text when buildingType is OTHER")
    private String buildingTypeOther;

    @NotNull(message = "Goal is required")
    @Schema(description = "What brings the user to Tanso", example = "MARGIN_ANALYTICS")
    private Goal goal;

    @Schema(description = "Custom goal text when a goal is OTHER")
    private String goalOther;
}
