/*
 * Tanso Core - open-source B2B SaaS monetization engine
 * Copyright (C) 2026  Douglas Baek
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.tansoflow.tansocore.integration.stripe.implementation;

import com.stripe.StripeClient;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.SetupIntent;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentMethodAttachParams;
import com.stripe.param.SetupIntentCreateParams;
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.StripeCustomer;
import com.tansoflow.tansocore.integration.stripe.StripeClientFactory;
import com.tansoflow.tansocore.integration.stripe.StripePaymentMethodService;
import com.tansoflow.tansocore.integration.stripe.StripeSyncService;
import com.tansoflow.tansocore.repository.CustomerRepository;
import com.tansoflow.tansocore.repository.StripeCustomerRepository;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class StripePaymentMethodServiceImpl implements StripePaymentMethodService {

    private final StripeClientFactory stripeClientFactory;
    private final StripeSyncService stripeSyncService;
    private final StripeCustomerRepository stripeCustomerRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final AccountService accountService;

    @Override
    public SetupIntentResult createSetupIntent(UUID accountId, UUID customerId) throws StripeException {
        StripeClient stripeClient = stripeClientFactory.forAccount(accountId);
        StripeCustomer stripeCustomer = ensureStripeCustomer(accountId, customerId);

        SetupIntent setupIntent = stripeClient.v1().setupIntents().create(
                SetupIntentCreateParams.builder()
                        .setCustomer(stripeCustomer.getStripeCustomerExternalId())
                        // Card only: redirect-based methods would demand a return_url,
                        // which a headless agent flow cannot provide
                        .addPaymentMethodType("card")
                        .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
                        .putMetadata("tanso_account_id", accountId.toString())
                        .putMetadata("tanso_customer_id", customerId.toString())
                        .build());
        return new SetupIntentResult(setupIntent.getId(), setupIntent.getClientSecret(),
                stripeCustomer.getStripeCustomerExternalId());
    }

    @Override
    @Transactional
    public void setDefaultPaymentMethod(UUID accountId, UUID customerId, String paymentMethodId)
            throws StripeException {
        StripeClient stripeClient = stripeClientFactory.forAccount(accountId);
        StripeCustomer stripeCustomer = ensureStripeCustomer(accountId, customerId);

        stripeClient.v1().paymentMethods().attach(paymentMethodId,
                PaymentMethodAttachParams.builder()
                        .setCustomer(stripeCustomer.getStripeCustomerExternalId())
                        .build());
        stripeClient.v1().customers().update(stripeCustomer.getStripeCustomerExternalId(),
                CustomerUpdateParams.builder()
                        .setInvoiceSettings(CustomerUpdateParams.InvoiceSettings.builder()
                                .setDefaultPaymentMethod(paymentMethodId)
                                .build())
                        .build());

        Customer customer = customerService.validateAndRetrieveCustomer(customerId.toString(), accountId.toString());
        customer.setStripeDefaultPaymentMethodId(paymentMethodId);
        customerRepository.save(customer);
        log.info("Set default payment method for customer {} on account {}", customerId, accountId);
    }

    @Override
    public PaymentResult chargeOffSession(UUID accountId, UUID customerId, String paymentMethodId,
                                          BigDecimal amount, String currency, String description,
                                          Map<String, String> metadata) throws StripeException {
        enforceSpendCap(accountId, amount);

        StripeClient stripeClient = stripeClientFactory.forAccount(accountId);
        StripeCustomer stripeCustomer = ensureStripeCustomer(accountId, customerId);

        long amountMinor = amount.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).longValueExact();
        PaymentIntentCreateParams.Builder params = PaymentIntentCreateParams.builder()
                .setAmount(amountMinor)
                .setCurrency(currency.toLowerCase())
                .setCustomer(stripeCustomer.getStripeCustomerExternalId())
                .setPaymentMethod(paymentMethodId)
                .setConfirm(true)
                .setOffSession(true)
                .setDescription(description);
        metadata.forEach(params::putMetadata);

        try {
            PaymentIntent intent = stripeClient.v1().paymentIntents().create(params.build());
            boolean succeeded = "succeeded".equals(intent.getStatus());
            return new PaymentResult(succeeded, intent.getId(),
                    succeeded ? null : "Payment status: " + intent.getStatus());
        } catch (CardException e) {
            // Declines and SCA challenges are expected outcomes on the
            // off-session path, not errors — the caller falls back to 402 + checkout
            log.info("Off-session payment declined for customer {} on account {}: {}",
                    customerId, accountId, e.getMessage());
            return new PaymentResult(false, e.getStripeError() != null
                    && e.getStripeError().getPaymentIntent() != null
                    ? e.getStripeError().getPaymentIntent().getId() : null, e.getMessage());
        }
    }

    @Override
    public HostedCheckout createTopupCheckoutSession(UUID accountId, UUID customerId, BigDecimal amount,
                                                     String currency, String description,
                                                     Map<String, String> metadata) throws StripeException {
        enforceSpendCap(accountId, amount);

        StripeClient stripeClient = stripeClientFactory.forAccount(accountId);
        StripeCustomer stripeCustomer = ensureStripeCustomer(accountId, customerId);
        AccountSetting settings = accountService.retrieveAccountSettings(accountId.toString());
        String successUrl = settings.getStripeCheckoutSuccessUrl() != null
                ? settings.getStripeCheckoutSuccessUrl() : "https://example.com/success";
        String cancelUrl = settings.getStripeCheckoutCancelUrl() != null
                ? settings.getStripeCheckoutCancelUrl() : "https://example.com/cancel";

        long amountMinor = amount.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).longValueExact();
        com.stripe.param.checkout.SessionCreateParams.Builder params =
                com.stripe.param.checkout.SessionCreateParams.builder()
                        .setMode(com.stripe.param.checkout.SessionCreateParams.Mode.PAYMENT)
                        .setCustomer(stripeCustomer.getStripeCustomerExternalId())
                        .setSuccessUrl(successUrl)
                        .setCancelUrl(cancelUrl)
                        .addLineItem(com.stripe.param.checkout.SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData.builder()
                                        .setCurrency(currency.toLowerCase())
                                        .setUnitAmount(amountMinor)
                                        .setProductData(com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                .setName(description)
                                                .build())
                                        .build())
                                .build());
        metadata.forEach(params::putMetadata);

        com.stripe.model.checkout.Session session = stripeClient.v1().checkout().sessions().create(params.build());
        return new HostedCheckout(session.getUrl(), session.getId());
    }

    /** Fail closed before money moves: reject any agent-initiated charge above the account cap. */
    private void enforceSpendCap(UUID accountId, BigDecimal amount) {
        AccountSetting settings = accountService.retrieveAccountSettings(accountId.toString());
        BigDecimal cap = settings != null ? settings.getAgentMaxTopupAmount() : null;
        if (cap != null && amount.compareTo(cap) > 0) {
            throw new AccessDeniedException(
                    "Amount " + amount + " exceeds this account's agent spend cap of " + cap);
        }
    }

    private StripeCustomer ensureStripeCustomer(UUID accountId, UUID customerId) throws StripeException {
        Customer customer = customerService.validateAndRetrieveCustomer(customerId.toString(), accountId.toString());
        StripeCustomer stripeCustomer = stripeCustomerRepository.findByCustomer(customer);
        if (stripeCustomer == null) {
            stripeCustomer = stripeSyncService.createStripeCustomer(accountId, customerId);
        }
        return stripeCustomer;
    }
}
