package com.tansoflow.tansocore.model.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GateErrorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void paymentRequiredCarriesCheckoutAndPoll() {
        GateError error = GateError.paymentRequired("https://checkout.stripe.com/c/pay_1",
                "https://api.example.com/api/v1/client/checkout-sessions/cs_1");

        assertThat(error.getCode()).isEqualTo("payment_required");
        assertThat(error.getGate()).isEqualTo("payment");
        assertThat(error.getAction()).isEqualTo("complete_checkout");
        assertThat(error.getUrl()).isEqualTo("https://checkout.stripe.com/c/pay_1");
        assertThat(error.getPoll()).isEqualTo("https://api.example.com/api/v1/client/checkout-sessions/cs_1");
        assertThat(error.getRetryAfter()).isNull();
        assertThat(error.getMessage()).isNotBlank();
    }

    @Test
    void paymentRequiredNoProcessorHasNoUrlAndNamesTheOperator() {
        GateError error = GateError.paymentRequiredNoProcessor();

        assertThat(error.getCode()).isEqualTo("payment_required");
        assertThat(error.getGate()).isEqualTo("payment");
        assertThat(error.getUrl()).isNull();
        assertThat(error.getPoll()).isNull();
        assertThat(error.getMessage()).contains("no payment processor").contains("operator");
    }

    @Test
    void budgetExceededTellsTheAgentToWait() {
        GateError error = GateError.budgetExceeded(3600L, "over budget");

        assertThat(error.getCode()).isEqualTo("budget_exceeded");
        assertThat(error.getGate()).isEqualTo("budget");
        assertThat(error.getAction()).isEqualTo("wait");
        assertThat(error.getRetryAfter()).isEqualTo(3600L);
        assertThat(error.getUrl()).isNull();
        assertThat(error.getPoll()).isNull();
    }

    @Test
    void spendCapExceededAsksToRaiseTheCap() {
        GateError error = GateError.spendCapExceeded("cap is 50");

        assertThat(error.getCode()).isEqualTo("spend_cap_exceeded");
        assertThat(error.getGate()).isEqualTo("budget");
        assertThat(error.getAction()).isEqualTo("raise_spend_cap");
        assertThat(error.getRetryAfter()).isNull();
    }

    @Test
    void otherCustomerTellsTheAgentToUseItsOwnReference() {
        GateError error = GateError.otherCustomer();

        assertThat(error.getCode()).isEqualTo("forbidden");
        assertThat(error.getGate()).isEqualTo("scope");
        assertThat(error.getAction()).isEqualTo("use_own_reference");
        assertThat(error.getMessage()).contains("customerReferenceId");
    }

    @Test
    void scopeDeniedAsksForScope() {
        GateError error = GateError.scopeDenied("This API key lacks the 'purchase' scope.");

        assertThat(error.getCode()).isEqualTo("scope_denied");
        assertThat(error.getGate()).isEqualTo("scope");
        assertThat(error.getAction()).isEqualTo("request_scope");
    }

    @Test
    void serializesWithSnakeCaseRetryAfterAndExplicitNulls() throws Exception {
        ApiResponse<Void> envelope = ApiResponse.<Void>builder()
                .success(false)
                .error(GateError.budgetExceeded(120L, "over budget"))
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(envelope));

        assertThat(json.get("success").asBoolean()).isFalse();
        JsonNode error = json.get("error");
        assertThat(error.get("code").asText()).isEqualTo("budget_exceeded");
        assertThat(error.get("gate").asText()).isEqualTo("budget");
        assertThat(error.get("action").asText()).isEqualTo("wait");
        assertThat(error.get("retry_after").asLong()).isEqualTo(120L);
        assertThat(error.has("retryAfter")).isFalse();
        assertThat(error.has("url")).isTrue();
        assertThat(error.get("url").isNull()).isTrue();
        assertThat(error.has("poll")).isTrue();
        assertThat(error.get("poll").isNull()).isTrue();
    }
}
