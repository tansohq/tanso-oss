package com.tansoflow.tansocore.integration.stripe;

import com.stripe.exception.StripeException;
import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.model.data.stripe.response.StripeApiKeysResponse;

public interface StripeService {
    void registerStripeApiKey(String clientStripeApiKey, Account account);

    StripeApiKeysResponse getStripeKeys(Account account);

    void deleteStripeKeys(Account account);

    void createNewWebhookEndpoint(Account account) throws StripeException;
}
