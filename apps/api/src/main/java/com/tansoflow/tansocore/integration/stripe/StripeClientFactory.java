package com.tansoflow.tansocore.integration.stripe;

import com.stripe.StripeClient;
import com.stripe.net.RequestOptions;
import com.tansoflow.tansocore.model.api.external.ExternalApiKeyType;
import com.tansoflow.tansocore.repository.ExternalApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StripeClientFactory {

    private final ExternalApiKeyRepository externalApiKeyRepository;

    private String apiKeyFor(UUID accountId) {
        return externalApiKeyRepository
                .findExternalApiKeyByKeyTypeAndAccount(ExternalApiKeyType.STRIPE_API_KEY.name(), accountId)
                .getKeyValue();
    }

    public StripeClient forAccount(UUID accountId) {
        return new StripeClient(apiKeyFor(accountId));
    }

    public RequestOptions requestOptionsForAccount(UUID accountId) {
        return RequestOptions.builder()
                .setApiKey(apiKeyFor(accountId))
                .build();
    }
}
