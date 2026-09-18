package com.tansoflow.tansocore.controller.client;

import com.tansoflow.tansocore.auth.CustomerAccessGuard;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.credit.CreditPurchaseResult;
import com.tansoflow.tansocore.model.credit.request.CreditPurchaseRequest;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.response.GateError;
import com.tansoflow.tansocore.service.client.ClientCreditService;
import com.tansoflow.tansocore.service.client.CreditPurchaseService;
import com.tansoflow.tansocore.service.internal.monetization.CreditPriceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditClientControllerGateEnvelopeTest {

    @Mock
    private CreditPurchaseService creditPurchaseService;
    @Mock
    private ClientCreditService clientCreditService;
    @Mock
    private CreditPriceService creditPriceService;
    @Spy
    private CustomerAccessGuard customerAccessGuard = new CustomerAccessGuard();

    @InjectMocks
    private CreditClientController controller;

    private final String accountId = UUID.randomUUID().toString();

    private UserContext customerKey() {
        return new UserContext(accountId, UUID.randomUUID().toString(), "agent_1", List.of("read", "purchase"), null);
    }

    private MockHttpServletRequest requestAt(String host) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/client/credits/purchases");
        request.setScheme("https");
        request.setServerName(host);
        request.setServerPort(443);
        return request;
    }

    private CreditPurchaseRequest hundredCredits() {
        CreditPurchaseRequest request = new CreditPurchaseRequest();
        request.setCredits(new BigDecimal("100"));
        return request;
    }

    @Test
    void checkoutFallbackOn402CarriesThePaymentGate() {
        when(creditPurchaseService.purchase(any(), eq("agent_1"), eq(accountId))).thenReturn(CreditPurchaseResult.builder()
                .completed(false)
                .credits(new BigDecimal("100"))
                .checkoutUrl("https://checkout.stripe.com/c/pay_xyz")
                .checkoutSessionId("cs_777")
                .build());

        ResponseEntity<ApiResponse<CreditPurchaseResult>> response =
                controller.purchaseCredits(customerKey(), hundredCredits(), requestAt("billing.example.com"));

        assertThat(response.getStatusCode().value()).isEqualTo(402);
        ApiResponse<CreditPurchaseResult> body = response.getBody();
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getData().getCheckoutUrl()).isEqualTo("https://checkout.stripe.com/c/pay_xyz");
        assertThat(body.getData().getCredits()).isEqualByComparingTo("100");

        GateError error = (GateError) body.getError();
        assertThat(error.getCode()).isEqualTo("payment_required");
        assertThat(error.getGate()).isEqualTo("payment");
        assertThat(error.getAction()).isEqualTo("complete_checkout");
        assertThat(error.getUrl()).isEqualTo("https://checkout.stripe.com/c/pay_xyz");
        assertThat(error.getPoll()).isEqualTo("https://billing.example.com/api/v1/client/checkout-sessions/cs_777");
        assertThat(error.getRetryAfter()).isNull();
    }

    @Test
    void noProcessorOn402HasNullUrlAndNamesTheOperator() {
        when(creditPurchaseService.purchase(any(), eq("agent_1"), eq(accountId))).thenReturn(CreditPurchaseResult.builder()
                .completed(false)
                .declineReason("This instance has no payment processor configured")
                .build());

        ResponseEntity<ApiResponse<CreditPurchaseResult>> response =
                controller.purchaseCredits(customerKey(), hundredCredits(), requestAt("billing.example.com"));

        assertThat(response.getStatusCode().value()).isEqualTo(402);
        assertThat(response.getBody().getData().getDeclineReason()).contains("no payment processor");

        GateError error = (GateError) response.getBody().getError();
        assertThat(error.getCode()).isEqualTo("payment_required");
        assertThat(error.getGate()).isEqualTo("payment");
        assertThat(error.getUrl()).isNull();
        assertThat(error.getPoll()).isNull();
        assertThat(error.getMessage()).contains("no payment processor").contains("operator");
    }

    @Test
    void completedPurchaseKeeps201WithoutAnError() {
        when(creditPurchaseService.purchase(any(), eq("agent_1"), eq(accountId))).thenReturn(CreditPurchaseResult.builder()
                .completed(true)
                .credits(new BigDecimal("100"))
                .grantId(UUID.randomUUID().toString())
                .build());

        ResponseEntity<ApiResponse<CreditPurchaseResult>> response =
                controller.purchaseCredits(customerKey(), hundredCredits(), requestAt("billing.example.com"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getError()).isNull();
    }
}
