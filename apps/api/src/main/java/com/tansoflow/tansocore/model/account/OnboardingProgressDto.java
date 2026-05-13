package com.tansoflow.tansocore.model.account;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "Tracks which onboarding steps have been completed")
public class OnboardingProgressDto {

    @Schema(description = "List of completed step keys", example = "[\"intake_completed\", \"mode_selected\"]")
    private List<String> completedSteps = new ArrayList<>();
}
