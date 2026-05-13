package com.tansoflow.tansocore.controller.tanso.data.stripe;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.integration.stripe.StripeService;
import com.tansoflow.tansocore.model.data.stripe.request.StripeApiKeyRegisterRequest;
import com.tansoflow.tansocore.model.data.stripe.response.StripeApiKeysResponse;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.response.Error;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/data/stripe")
@PreAuthorize("hasRole('TANSO_UI')")
@Tag(name = "Stripe Data", description = "Operations for managing Stripe integration and keys")
public class StripeController {
    private final StripeService stripeService;
    private final AccountService accountService;

    @PostMapping("/webhook/register")
    @Operation(summary = "Register Stripe webhook", description = "Registers a new webhook endpoint in the client's Stripe account", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Successfully created and registered webhook endpoint"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> postRegisterWebhook(@AuthenticationPrincipal UserContext userContext) {
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder().build();
        Account account = accountService.retrieveAccount(userContext.getAccountId());

        try {
            stripeService.createNewWebhookEndpoint(account);
            apiResponse.setSuccess(true);
            return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
        } catch (Exception e) {
            log.error("Error occurred while registering webhook endpoint to client's stripe account", e);
            apiResponse.setError(new Error(e.getMessage()));
            apiResponse.setSuccess(false);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(apiResponse);
        }
    }

    @GetMapping("/api")
    @Operation(summary = "Get Stripe keys", description = "Retrieves the Stripe API key and webhook key (masked) for the account", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved keys"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)
    })
    public ResponseEntity<ApiResponse<StripeApiKeysResponse>> getStripeApiKey(@AuthenticationPrincipal UserContext userContext) {
        Account account = accountService.retrieveAccount(userContext.getAccountId());
        StripeApiKeysResponse response = stripeService.getStripeKeys(account);
        ApiResponse<StripeApiKeysResponse> apiResponse = ApiResponse.<StripeApiKeysResponse>builder()
                .success(true)
                .data(response)
                .build();

        return ResponseEntity.ok(apiResponse);
    }


    @PostMapping("/api")
    @Operation(summary = "Register Stripe API key", description = "Registers a Stripe API key to the client's account", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Successfully registered API key"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> postRegisterStripeApiKey(@AuthenticationPrincipal UserContext userContext, @Valid @RequestBody StripeApiKeyRegisterRequest registerStripeApiKeyRequest) {
        try {
            ApiResponse<Void> apiResponse = ApiResponse.<Void>builder().build();
            Account account = accountService.retrieveAccount(userContext.getAccountId());
            stripeService.registerStripeApiKey(registerStripeApiKeyRequest.getClientStripeApiKey(), account);
            apiResponse.setSuccess(true);
            return ResponseEntity.status(HttpStatus.CREATED).body(apiResponse);
        } catch (Exception e) {
            log.error("Error occurred while registering stripe api key to client's account", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.<Void>builder().error(new Error(e.getMessage())).build());
        }
    }

    @DeleteMapping("/api")
    @Operation(summary = "Delete Stripe keys", description = "Deletes all Stripe keys associated with the account", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully deleted keys"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access Denied.", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> deleteStripeApiKeys(@AuthenticationPrincipal UserContext userContext) {
        ApiResponse<Void> apiResponse = ApiResponse.<Void>builder().build();
        Account account = accountService.retrieveAccount(userContext.getAccountId());
        stripeService.deleteStripeKeys(account);
        apiResponse.setSuccess(true);
        return ResponseEntity.status(HttpStatus.OK).body(apiResponse);
    }

}
