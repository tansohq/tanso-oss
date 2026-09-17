package com.tansoflow.tansocore.controller.client;

import com.tansoflow.tansocore.auth.CustomerAccessGuard;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.integration.stripe.StripeSyncService;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.response.GateError;
import com.tansoflow.tansocore.model.subscription.SubscriptionDto;
import com.tansoflow.tansocore.model.subscription.request.ClientSubscriptionRequest;
import com.tansoflow.tansocore.model.subscription.response.SubscribedCustomerResponse;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import com.tansoflow.tansocore.service.internal.monetization.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionClientControllerGateEnvelopeTest {

    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private AccountService accountService;
    @Mock
    private StripeSyncService stripeSyncService;
    @Spy
    private CustomerAccessGuard customerAccessGuard = new CustomerAccessGuard();

    @InjectMocks
    private SubscriptionClientController controller;

    private final String accountId = UUID.randomUUID().toString();

    private UserContext customerKey() {
        return new UserContext(accountId, UUID.randomUUID().toString(), "agent_1", List.of("read", "purchase"), null);
    }

    private MockHttpServletRequest requestAt(String host) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/client/subscriptions");
        request.setScheme("https");
        request.setServerName(host);
        request.setServerPort(443);
        return request;
    }

    private SubscribedCustomerResponse checkoutOutcome(String checkoutUrl, String sessionId) {
        SubscribedCustomerResponse outcome = new SubscribedCustomerResponse();
        outcome.setCheckoutUrl(checkoutUrl);
        outcome.setCheckoutSessionId(sessionId);
        return outcome;
    }

    @Test
    void checkoutOn402CarriesThePaymentGate() {
        ClientSubscriptionRequest request = new ClientSubscriptionRequest();
        request.setPlanId("growth");
        when(subscriptionService.clientSubscribeCustomer(any(), eq(accountId)))
                .thenReturn(checkoutOutcome("https://checkout.stripe.com/c/pay_abc", "cs_123"));

        ResponseEntity<ApiResponse<SubscribedCustomerResponse>> response =
                controller.createSubscription(customerKey(), request, requestAt("billing.example.com"));

        assertThat(response.getStatusCode().value()).isEqualTo(402);
        ApiResponse<SubscribedCustomerResponse> body = response.getBody();
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getData().getCheckoutUrl()).isEqualTo("https://checkout.stripe.com/c/pay_abc");
        assertThat(body.getData().getCheckoutSessionId()).isEqualTo("cs_123");

        GateError error = (GateError) body.getError();
        assertThat(error.getCode()).isEqualTo("payment_required");
        assertThat(error.getGate()).isEqualTo("payment");
        assertThat(error.getAction()).isEqualTo("complete_checkout");
        assertThat(error.getUrl()).isEqualTo("https://checkout.stripe.com/c/pay_abc");
        assertThat(error.getPoll()).isEqualTo("https://billing.example.com/api/v1/client/checkout-sessions/cs_123");
        assertThat(error.getRetryAfter()).isNull();
    }

    @Test
    void activeSubscriptionKeeps201WithoutAnError() {
        ClientSubscriptionRequest request = new ClientSubscriptionRequest();
        request.setPlanId("free");
        SubscriptionDto active = new SubscriptionDto();
        active.setIsActive(true);
        SubscribedCustomerResponse outcome = new SubscribedCustomerResponse();
        outcome.setSubscription(active);
        when(subscriptionService.clientSubscribeCustomer(any(), eq(accountId))).thenReturn(outcome);

        ResponseEntity<ApiResponse<SubscribedCustomerResponse>> response =
                controller.createSubscription(customerKey(), request, requestAt("billing.example.com"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getError()).isNull();
    }
}
