package com.tansoflow.tansocore.model.auth.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Response containing the JWT authentication token")
public class JwtResponse {
    @Schema(description = "The JSON Web Token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;

    @Schema(description = "The type of the token", example = "Bearer")
    private String type = "Bearer";
}
