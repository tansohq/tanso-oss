package com.tansoflow.tansocore.model.account.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Response containing the account's API key")
public class AccountApiKeyResponse {
    @Schema(description = "The API key value", example = "sk_live_123456789")
    private String apiKey;

    @Schema(description = "The type of the API key", example = "secret")
    private String keyType;
}
