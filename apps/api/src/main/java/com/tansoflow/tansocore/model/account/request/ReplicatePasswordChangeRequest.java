package com.tansoflow.tansocore.model.account.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request to replicate password change from another environment")
public class ReplicatePasswordChangeRequest {
    @NotBlank(message = "User email is required")
    @Email(message = "Invalid email format")
    @Schema(description = "The email address identifying the user", example = "user@example.com")
    private String userEmail;

    @NotBlank(message = "New password is required")
    @Schema(description = "The new password (plaintext, will be hashed by target)", example = "newPassword123")
    private String newPassword;
}

