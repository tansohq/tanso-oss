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
package com.tansoflow.tansocore.service.client.implementation;

import com.stripe.exception.StripeException;
import com.tansoflow.tansocore.entity.CheckoutSession;
import com.tansoflow.tansocore.entity.CreditPool;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.integration.stripe.StripePaymentMethodService;
import com.tansoflow.tansocore.model.credit.CreditGrantDto;
import com.tansoflow.tansocore.model.credit.CreditPurchaseResult;
import com.tansoflow.tansocore.model.credit.request.CreditGrantRequest;
import com.tansoflow.tansocore.model.credit.request.CreditPurchaseRequest;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.repository.CheckoutSessionRepository;
import com.tansoflow.tansocore.repository.CreditPoolRepository;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.monetization.CreditPriceService;
import com.tansoflow.tansocore.service.internal.monetization.CreditService;
import com.tansoflow.tansocore.service.client.CreditPurchaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CreditPurchaseServiceImpl implements CreditPurchaseService {

    private final CreditPoolRepository creditPoolRepository;
    private final CreditPriceService creditPriceService;
    private final CreditService creditService;
    private final CustomerService customerService;
    private final StripePaymentMethodService stripePaymentMethodService;
    private final CheckoutSessionRepository checkoutSessionRepository;

    @Override
    @Transactional
    public CreditPurchaseResult purchase(CreditPurchaseRequest request, String customerReferenceId,
                                         String accountId) {
        UUID accountUuid = UUID.fromString(accountId);
        Customer customer = customerService
                .retrieveCustomerByExternalClientCustomerIdAndAccount(customerReferenceId, accountId);

        CreditPool pool = creditPoolRepository
                .findByIdAndAccountId(UUID.fromString(request.getCreditPoolId()), accountUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Credit pool not found: " + request.getCreditPoolId()));
        if (pool.getCustomer() == null || !pool.getCustomer().getId().equals(customer.getId())) {
            throw new ResourceNotFoundException("Credit pool not found: " + request.getCreditPoolId());
        }

        var price = creditPriceService.resolvePrice(accountUuid, pool.getDenomination(), Instant.now())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No published price for denomination '" + pool.getDenomination()
                                + "' — the operator must publish a price book entry before credits can be purchased"));

        BigDecimal amount = request.getCredits().multiply(price.pricePerCredit())
                .setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(new BigDecimal("0.50")) < 0) {
            throw new IllegalArgumentException(
                    "Purchase amount " + amount + " " + price.currency()
                            + " is below the 0.50 payment minimum — buy more credits at once");
        }

        Map<String, String> metadata = Map.of(
                "tanso_account_id", accountId,
                "tanso_customer_id", customer.getId().toString(),
                "tanso_credit_pool_id", pool.getId().toString(),
                "tanso_credits", request.getCredits().toPlainString(),
                "tanso_purpose", "credit_topup");

        String paymentMethod = request.getPaymentMethodId() != null
                ? request.getPaymentMethodId()
                : customer.getStripeDefaultPaymentMethodId();

        String description = request.getCredits().stripTrailingZeros().toPlainString()
                + " " + pool.getDenomination() + " top-up";

        try {
            if (paymentMethod != null) {
                StripePaymentMethodService.PaymentResult charge = stripePaymentMethodService.chargeOffSession(
                        accountUuid, customer.getId(), paymentMethod, amount, price.currency(), description, metadata);
                if (charge.succeeded()) {
                    CreditGrantDto grant = grantPurchasedCredits(request, pool, price.pricePerCredit(),
                            price.currency(), accountId, "pi_" + charge.paymentIntentId());
                    return CreditPurchaseResult.builder()
                            .completed(true)
                            .credits(request.getCredits())
                            .pricePerCredit(price.pricePerCredit())
                            .amountCharged(amount)
                            .currency(price.currency())
                            .grantId(grant.getId())
                            .paymentIntentId(charge.paymentIntentId())
                            .build();
                }
                log.info("Off-session credit purchase declined for customer {}: {} — falling back to hosted checkout",
                        customer.getId(), charge.failureMessage());
            }

            StripePaymentMethodService.HostedCheckout hosted = stripePaymentMethodService
                    .createTopupCheckoutSession(accountUuid, customer.getId(), amount, price.currency(),
                            description, metadata);

            CheckoutSession session = new CheckoutSession();
            session.setAccountId(accountUuid);
            session.setCustomerId(customer.getId());
            session.setPurpose(CheckoutSession.PURPOSE_CREDIT_TOPUP);
            session.setCreditPoolId(pool.getId());
            session.setCredits(request.getCredits());
            session.setStripeSessionId(hosted.stripeSessionId());
            session.setCheckoutUrl(hosted.url());
            checkoutSessionRepository.save(session);

            return CreditPurchaseResult.builder()
                    .completed(false)
                    .credits(request.getCredits())
                    .pricePerCredit(price.pricePerCredit())
                    .amountCharged(amount)
                    .currency(price.currency())
                    .checkoutUrl(hosted.url())
                    .checkoutSessionId(session.getId().toString())
                    .declineReason(paymentMethod != null ? "off-session charge declined" : null)
                    .build();
        } catch (StripeException e) {
            throw new IllegalStateException("Stripe error during credit purchase: " + e.getMessage(), e);
        }
    }

    private CreditGrantDto grantPurchasedCredits(CreditPurchaseRequest request, CreditPool pool,
                                                 BigDecimal pricePerCredit, String currency,
                                                 String accountId, String idempotencyKey) {
        CreditGrantRequest grant = new CreditGrantRequest();
        grant.setCreditPoolId(pool.getId().toString());
        grant.setAmount(request.getCredits());
        grant.setGrantType("PURCHASED");
        grant.setUnitPrice(pricePerCredit);
        grant.setCurrency(currency);
        grant.setDescription("Agent credit top-up");
        grant.setIdempotencyKey(idempotencyKey);
        return creditService.grantCredits(grant, accountId);
    }
}
