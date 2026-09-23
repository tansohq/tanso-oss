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
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.Price;
import com.stripe.model.Product;
import com.stripe.model.SetupIntent;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.InvoiceCreateParams;
import com.stripe.param.InvoiceItemCreateParams;
import com.stripe.param.PaymentMethodAttachParams;
import com.stripe.param.PriceCreateParams;
import com.stripe.param.ProductCreateParams;
import com.stripe.param.SubscriptionCancelParams;
import com.stripe.param.SubscriptionCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.billing.MeterCreateParams;
import com.stripe.param.billing.MeterEventCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Feature;
import com.tansoflow.tansocore.entity.Invoice;
import com.tansoflow.tansocore.entity.InvoiceItem;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.PlanFeatureRule;
import com.tansoflow.tansocore.entity.StripeCustomer;
import com.tansoflow.tansocore.entity.StripeInvoice;
import com.tansoflow.tansocore.entity.StripeMeter;
import com.tansoflow.tansocore.entity.StripePrice;
import com.tansoflow.tansocore.entity.StripeProduct;
import com.tansoflow.tansocore.entity.StripeSubscription;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.integration.stripe.CheckoutReturnUrls;
import com.tansoflow.tansocore.integration.stripe.StripeClientFactory;
import com.tansoflow.tansocore.integration.stripe.StripeSyncService;
import com.tansoflow.tansocore.model.data.stripe.StripePaymentLinkDto;
import com.tansoflow.tansocore.model.data.stripe.StripeUpgradeCharge;
import com.tansoflow.tansocore.model.monetization.pricing.GraduatedPricingModel;
import com.tansoflow.tansocore.model.monetization.pricing.PricingModel;
import com.tansoflow.tansocore.model.monetization.pricing.SimpleUsageModel;
import com.tansoflow.tansocore.model.plan.BillingTiming;
import com.tansoflow.tansocore.repository.FeatureRepository;
import com.tansoflow.tansocore.repository.InvoiceItemRepository;
import com.tansoflow.tansocore.repository.InvoiceRepository;
import com.tansoflow.tansocore.repository.PlanFeatureRuleRepository;
import com.tansoflow.tansocore.repository.StripeCustomerRepository;
import com.tansoflow.tansocore.repository.StripeInvoiceRepository;
import com.tansoflow.tansocore.repository.StripeMeterRepository;
import com.tansoflow.tansocore.repository.StripePriceRepository;
import com.tansoflow.tansocore.repository.StripeProductPlansRepository;
import com.tansoflow.tansocore.repository.StripeSubscriptionRepository;
import com.tansoflow.tansocore.repository.SubscriptionRepository;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.monetization.InvoiceService;
import com.tansoflow.tansocore.service.internal.monetization.PlanService;
import com.tansoflow.tansocore.util.monetization.RuleCalculationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class StripeSyncServiceImpl implements StripeSyncService {
    private final StripeCustomerRepository stripeCustomerRepository;
    private final StripeProductPlansRepository stripeProductPlansRepository;
    private final StripeSubscriptionRepository stripeSubscriptionRepository;
    private final AccountService accountService;
    private final CustomerService customerService;
    private final StripeClientFactory stripeClientFactory;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanService planService;
    private final InvoiceService invoiceService;
    private final StripeInvoiceRepository stripeInvoiceRepository;
    private final StripePriceRepository stripePriceRepository;
    private final StripeMeterRepository stripeMeterRepository;
    private final PlanFeatureRuleRepository planFeatureRuleRepository;
    private final FeatureRepository featureRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private final jakarta.persistence.EntityManager entityManager;

    @Override
    public void syncStripeSubscriptionTansoSubscription(String stripeSubscriptionId, String tansoSubscription, String accountId) {
        if (stripeSubscriptionId == null || tansoSubscription == null || accountId == null
                || stripeSubscriptionRepository.existsStripeSubscriptionByStripeSubscriptionExternalId(stripeSubscriptionId)) {
            log.warn("Aborting sync due to null parameters: stripeSubscriptionId={}, tansoSubscriptionId={}, accountId={}",
                    stripeSubscriptionId, tansoSubscription, accountId);
            return;
        }

        StripeSubscription stripeSubscription = new StripeSubscription();
        Subscription subscription = subscriptionRepository.findSubscriptionByUuidAndAccountId(UUID.fromString(tansoSubscription), UUID.fromString(accountId));

        if (subscription == null) {
            throw new IllegalArgumentException("Subscription not found with id: " + tansoSubscription);
        }

        stripeSubscription.setStripeSubscriptionExternalId(stripeSubscriptionId);
        stripeSubscription.setSubscription(subscription);
        stripeSubscription.setAccount(subscription.getAccount());
        stripeSubscriptionRepository.save(stripeSubscription);
    }

    @Override
    public void saveStripeInvoice(String stripeInvoiceId, String tansoInvoiceId, String accountId) {
        Invoice invoice = invoiceService.retrieveInvoiceByInvoiceIdAndAccount(tansoInvoiceId, accountId);
        StripeInvoice stripeInvoice = new StripeInvoice();
        stripeInvoice.setInvoice(invoice);
        stripeInvoice.setStripeInvoiceExternalId(stripeInvoiceId);
        stripeInvoiceRepository.save(stripeInvoice);
    }

    @Override
    @Transactional
    public StripeCustomer syncStripeCustomerTansoCustomer(String stripeCustomerId, String tansoCustomer, String accountId) {
        Account account = accountService.retrieveAccount(accountId);
        Customer customer = customerService.validateAndRetrieveCustomer(tansoCustomer, accountId);

        StripeCustomer stripeCustomerEntity = new StripeCustomer();
        stripeCustomerEntity.setStripeCustomerExternalId(stripeCustomerId);
        stripeCustomerEntity.setAccount(account);
        stripeCustomerEntity.setCustomer(customer);
        stripeCustomerEntity.setSyncedAt(Instant.now());

        StripeCustomer saved = stripeCustomerRepository.saveAndFlush(stripeCustomerEntity);
        log.info("Saved StripeCustomer entity: id={}, stripeId={}, customerId={}",
                saved.getStripeCustomerExternalId(), saved.getStripeCustomerExternalId(), saved.getCustomer().getId());

        StripeCustomer reloaded = stripeCustomerRepository.findStripeCustomerById(saved.getId());
        log.info("Reloaded StripeCustomer from DB: {}", reloaded);

        return saved;
    }


    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StripeCustomer createStripeCustomer(UUID accountId, UUID tansoCustomerId) throws StripeException {
        try {
            StripeClient stripeClient = stripeClientFactory.forAccount(accountId);

            Customer customer = customerService.validateAndRetrieveCustomer(tansoCustomerId.toString(), accountId.toString());

            if (stripeCustomerRepository.existsStripeCustomerByCustomerAndAccount(customer, customer.getAccount())) {
                return stripeCustomerRepository.findByCustomer(customer);
            }

            // Agent-created customers often have no name; "null null" on an invoice is not a name.
            String name = ((customer.getFirstName() == null ? "" : customer.getFirstName()) + " "
                    + (customer.getLastName() == null ? "" : customer.getLastName())).trim();
            CustomerCreateParams params = CustomerCreateParams.builder()
                    .setName(name.isEmpty() ? customer.getExternalClientCustomerId() : name)
                    .setEmail(customer.getEmail())
                    .putMetadata("tanso_account_id", accountId.toString())
                    .putMetadata("tanso_customer_id", tansoCustomerId.toString())
                    .build();
            com.stripe.model.Customer stripeCustomer = stripeClient.v1().customers().create(params);

            return syncStripeCustomerTansoCustomer(stripeCustomer.getId(), tansoCustomerId.toString(), accountId.toString());
        } catch (StripeException stripeException) {
            log.error("Stripe occurred while creating new customer:", stripeException);
            throw stripeException;
        } catch (Exception exception) {
            log.error("Error occurred while creating new customer:", exception);
            throw new RuntimeException(exception);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StripePaymentLinkDto syncNewInvoice(UUID invoiceId, UUID accountId) throws StripeException {
        StripeInvoice stripeInvoiceEntity = new StripeInvoice();

        Invoice invoice = invoiceService.retrieveInvoiceByInvoiceIdAndAccount(invoiceId.toString(), accountId.toString());

        if (stripeInvoiceRepository.existsStripeInvoiceByInvoice(invoice)) {
            return retrieveStripeInvoiceHostedUrl(invoiceId.toString(), accountId.toString());
        }
        stripeInvoiceEntity.setInvoice(invoice);
        StripeClient client = stripeClientFactory.forAccount(accountId);
        Subscription subscription = invoice.getSubscription();
        StripeCustomer stripeCustomer = stripeCustomerRepository.findByCustomer(subscription.getCustomer());
        if (stripeCustomer == null) {
            stripeCustomer = createStripeCustomer(accountId, subscription.getCustomer().getId());
        }
        String stripeCustomerId = stripeCustomer.getStripeCustomerExternalId();

        InvoiceCreateParams invoiceParams = InvoiceCreateParams.builder()
                .setCustomer(stripeCustomerId)
                .setCurrency(invoice.getCurrency() == null ? "usd" : invoice.getCurrency().toLowerCase())
                .setCollectionMethod(InvoiceCreateParams.CollectionMethod.SEND_INVOICE)
                .setDaysUntilDue(3L)
                .setPaymentSettings(
                        InvoiceCreateParams.PaymentSettings.builder()
                                .addPaymentMethodType(InvoiceCreateParams.PaymentSettings.PaymentMethodType.CARD)
                                // add others if support them in future
                                .build()
                )
                .putMetadata("tanso_invoice_id", invoice.getId().toString())
                .putMetadata("tanso_subscription_id", subscription.getId().toString())
                .putMetadata("tanso_account_id", accountId.toString())
                .putMetadata("tanso_customer_id", subscription.getCustomer().getId().toString())
                .build();

        com.stripe.model.Invoice stripeInvoice = client.v1().invoices().create(invoiceParams);

        String currency = invoice.getCurrency() == null ? "usd" : invoice.getCurrency().toLowerCase();
        List<InvoiceItem> items = invoiceItemRepository.findAllByInvoice(invoice);
        if (items.isEmpty()) {
            // Fallback for legacy invoices without items
            InvoiceItemCreateParams itemParams = InvoiceItemCreateParams.builder()
                    .setCustomer(stripeCustomerId)
                    .setCurrency(currency)
                    .setInvoice(stripeInvoice.getId())
                    .setAmount(invoice.getAmount().movePointRight(2).longValueExact())
                    .setDescription("Invoice " + invoice.getId())
                    .putMetadata("tanso_invoice_id", invoice.getId().toString())
                    .putMetadata("tanso_subscription_id", subscription.getId().toString())
                    .putMetadata("tanso_account_id", String.valueOf(accountId))
                    .putMetadata("tanso_customer_id", subscription.getCustomer().getId().toString())
                    .build();
            client.v1().invoiceItems().create(itemParams);
        } else {
            for (InvoiceItem item : items) {
                InvoiceItemCreateParams itemParams = InvoiceItemCreateParams.builder()
                        .setCustomer(stripeCustomerId)
                        .setCurrency(currency)
                        .setInvoice(stripeInvoice.getId())
                        .setAmount(item.getChargeAmount().movePointRight(2).longValueExact())
                        .setDescription(item.getDescription())
                        .putMetadata("tanso_invoice_id", invoice.getId().toString())
                        .putMetadata("tanso_subscription_id", subscription.getId().toString())
                        .putMetadata("tanso_account_id", String.valueOf(accountId))
                        .putMetadata("tanso_customer_id", subscription.getCustomer().getId().toString())
                        .build();
                client.v1().invoiceItems().create(itemParams);
            }
        }

        // Finalize
        com.stripe.model.Invoice stripeInvoiceFinalized = stripeInvoice.finalizeInvoice().sendInvoice();
        stripeInvoiceEntity.setStripeInvoiceExternalId(stripeInvoiceFinalized.getId());
        stripeInvoiceRepository.save(stripeInvoiceEntity);

        String hostedUrl = stripeInvoiceFinalized.getHostedInvoiceUrl();

        StripePaymentLinkDto stripePaymentLinkDto = new StripePaymentLinkDto();
        stripePaymentLinkDto.setPaymentLink(hostedUrl);
        return stripePaymentLinkDto;
    }

    @Override
    public StripePaymentLinkDto retrieveStripeInvoiceHostedUrl(String invoiceId, String accountId) throws StripeException {
        StripeClient stripeClient = stripeClientFactory.forAccount(UUID.fromString(accountId));
        Invoice invoice = invoiceService.retrieveInvoiceByInvoiceIdAndAccount(invoiceId, accountId);

        StripeInvoice stripeInvoice = stripeInvoiceRepository.findStripeInvoiceByInvoice(invoice);

        String hostedUrl = stripeClient.v1().invoices().retrieve(stripeInvoice.getStripeInvoiceExternalId()).getHostedInvoiceUrl();

        StripePaymentLinkDto stripePaymentLinkDto = new StripePaymentLinkDto();
        stripePaymentLinkDto.setPaymentLink(hostedUrl);
        return stripePaymentLinkDto;
    }

    @Transactional
    @Override
    public StripePaymentLinkDto retrieveStripeSession(String invoiceId, String accountId) throws StripeException {
        StripeClient stripeClient = stripeClientFactory.forAccount(UUID.fromString(accountId));
        Invoice invoice = invoiceService.retrieveInvoiceByInvoiceIdAndAccount(invoiceId, accountId);

        // Look up the linked Stripe Invoice
        StripeInvoice stripeInvoice = stripeInvoiceRepository.findStripeInvoiceByInvoice(invoice);
        if (stripeInvoice == null) {
            throw new IllegalStateException(
                    "No Stripe invoice linked for Tanso invoice " + invoiceId +
                    ". The Stripe subscription may still be initializing — please retry shortly.");
        }

        // Retrieve the Stripe invoice to get the hosted payment URL
        com.stripe.model.Invoice stripeInvoiceObj = stripeClient.v1().invoices()
                .retrieve(stripeInvoice.getStripeInvoiceExternalId());

        String hostedUrl = stripeInvoiceObj.getHostedInvoiceUrl();

        StripePaymentLinkDto stripePaymentLinkDto = new StripePaymentLinkDto();
        stripePaymentLinkDto.setPaymentLink(hostedUrl);
        return stripePaymentLinkDto;
    }

    @Override
    @Transactional
    public Product createStripeProduct(String planId, String accountId) throws StripeException {
        StripeClient stripeClient = stripeClientFactory.forAccount(UUID.fromString(accountId));
        Plan plan = planService.retrievePlan(UUID.fromString(accountId), UUID.fromString(planId));

        Map<String, String> metadata = Map.of(
                "tanso_account_id", accountId,
                "tanso_plan_id", plan.getId().toString()
        );

        ProductCreateParams productParams = ProductCreateParams.builder()
                .setName(plan.getName())
                .putAllMetadata(metadata)
                .build();

        Product product = stripeClient.v1().products().create(productParams);
        StripeProduct productsPlan = new StripeProduct();
        productsPlan.setPlan(plan);
        productsPlan.setStripeProductExternalId(product.getId());
        productsPlan.setAccount(plan.getAccount());

        stripeProductPlansRepository.save(productsPlan);

        return product;
    }

    // @Override
    public void syncPaymentMethodToDefault(com.stripe.model.InvoicePayment paymentObj, String accountId) throws StripeException {
        StripeClient client = stripeClientFactory.forAccount(UUID.fromString(accountId));

        StripeInvoice stripeInvoice = stripeInvoiceRepository.findStripeInvoiceByStripeInvoiceId(paymentObj.getInvoice());
        Customer customer = stripeInvoice.getInvoice().getSubscription().getCustomer();
        String stripeCustomerId = stripeCustomerRepository.findByCustomer(customer).getStripeCustomerExternalId();

        String paymentIntentId = paymentObj.getPayment().getPaymentIntent();
        PaymentIntent pi = client.v1().paymentIntents().retrieve(paymentIntentId);

        String paymentMethodId = pi.getPaymentMethod();
        PaymentMethod pm = client.v1().paymentMethods().retrieve(paymentMethodId);

        // Attach the PM to this customer (idempotent-ish: will error if already attached elsewhere)
        PaymentMethodAttachParams attachParams =
                PaymentMethodAttachParams.builder()
                        .setCustomer(stripeCustomerId)
                        .build();

        pm.attach(attachParams);

        // Set customer's default PM for future invoices/off-session charges
        CustomerUpdateParams params =
                CustomerUpdateParams.builder()
                        .setInvoiceSettings(
                                CustomerUpdateParams.InvoiceSettings.builder()
                                        .setDefaultPaymentMethod(paymentMethodId)
                                        .build()
                        )
                        .build();

        client.v1().customers().update(stripeCustomerId, params);
    }

    @Transactional
    // @Override
    public StripePaymentLinkDto createStripeSubscriptionPaymentLink(String accountId, String subscriptionId) throws StripeException {
        StripeClient stripeClient = stripeClientFactory.forAccount(UUID.fromString(accountId));

        AccountSetting accountSetting = accountService.retrieveAccountSettings(accountId);


        String successUrl = CheckoutReturnUrls.successUrl(accountSetting, CheckoutReturnUrls.KIND_PAYMENT);
        String cancelUrl = CheckoutReturnUrls.cancelUrl(accountSetting);

        if (!successUrl.contains("{CHECKOUT_SESSION_ID}")) {
            successUrl = successUrl + (successUrl.contains("?") ? "&" : "?") + "session_id={CHECKOUT_SESSION_ID}";
        }

        try {
            Subscription subscription = subscriptionRepository.findSubscriptionByUuidAndAccountId(UUID.fromString(subscriptionId), UUID.fromString(accountId));

            if (subscription == null) {
                throw new IllegalArgumentException("Subscription not found");
            }

            StripeCustomer stripeCustomer = stripeCustomerRepository.findByCustomer(subscription.getCustomer());

            if (stripeCustomer == null) {
                throw new IllegalArgumentException("stripeCustomer not found");
            }

            Plan plan = subscription.getPlan();

            if (plan == null) {
                throw new IllegalArgumentException("Plan not found");
            }

            String productId;
            StripeProduct stripeProduct = stripeProductPlansRepository.findStripeProductByPlan(plan);

            if (stripeProduct == null) {
                productId = createStripeProduct(plan.getId().toString(), accountId).getId();
            } else {
                productId = stripeProduct.getStripeProductExternalId();
            }

            Price price = createStripePrice(plan, productId);
            // Canonical metadata for Tanso <-> Stripe mapping
            Map<String, String> metadata = Map.of(
                    "tanso_account_id", accountId,
                    "tanso_customer_id", subscription.getCustomer().getId().toString(),
                    "tanso_subscription_id", subscription.getId().toString()
            );

            SessionCreateParams.SubscriptionData subscriptionData =
                    SessionCreateParams.SubscriptionData.builder()
                            .putAllMetadata(metadata)
                            .setProrationBehavior(SessionCreateParams.SubscriptionData.ProrationBehavior.NONE)
                            .build();

            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION) // subscription mode
                    .addLineItem(SessionCreateParams
                            .LineItem.builder()
                            .setPrice(price.getId())
                            .setQuantity(1L)
                            .build())
                    .putAllMetadata(metadata)
                    .addAllPaymentMethodType(List.of(
                            SessionCreateParams.PaymentMethodType.CARD
                    ))
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .setCustomer(stripeCustomer.getStripeCustomerExternalId())
                    .setSubscriptionData(subscriptionData)
                    .build();

            Session session = stripeClient.v1().checkout().sessions().create(params);

            String paymentUrl = session.getUrl();

            StripePaymentLinkDto stripePaymentLinkDto = new StripePaymentLinkDto();
            stripePaymentLinkDto.setPaymentLink(paymentUrl);

            return stripePaymentLinkDto;
        } catch (StripeException stripeException) {
            log.error("Stripe occurred while creating new subscription:", stripeException);
            throw stripeException;
        } catch (Exception exception) {
            log.error("Error occurred while creating new subscription:", exception);
            throw new RuntimeException(exception);
        }
    }

    @Override
    public Price createStripePrice(Plan plan, String productId) throws StripeException {
        StripeClient stripeClient = stripeClientFactory.forAccount(plan.getAccount().getId());

        BigDecimal amount = plan.getPriceAmount();
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("Price amount cannot have more than 2 decimal places");
        }
        long amountInCents = amount.movePointRight(2).longValueExact();

        PriceCreateParams.Builder priceParamsBuilder = PriceCreateParams.builder()
                .setCurrency("usd")// or your preferred currency
                .setProduct(productId)
                .setUnitAmount(amountInCents);// Convert to cents

        // Make it RECURRING for subscriptions
        priceParamsBuilder.setRecurring(
                PriceCreateParams.Recurring.builder()
                        .setInterval(PriceCreateParams.Recurring.Interval.MONTH) // or YEAR, DAY, WEEK
                        .setIntervalCount(plan.getIntervalMonths().longValue()) // billing frequency
                        .build()
        );

        return stripeClient.v1().prices().create(priceParamsBuilder.build());
    }

    @Override
    public boolean isSubscriptionLinked(Subscription subscription) {
        return stripeSubscriptionRepository.existsStripeSubscriptionBySubscription(subscription);
    }

    @Override
    public StripePaymentLinkDto updateCustomerPayment(String accountId, String customerId) throws StripeException {
        StripeClient stripe = stripeClientFactory.forAccount(UUID.fromString(accountId));
        StripeCustomer stripeCustomer = stripeCustomerRepository.findStripeCustomerById(UUID.fromString(customerId));

        AccountSetting accountSetting = accountService.retrieveAccountSettings(accountId);

        String successUrl = CheckoutReturnUrls.successUrl(accountSetting, CheckoutReturnUrls.KIND_SETUP);
        String cancelUrl = CheckoutReturnUrls.cancelUrl(accountSetting);

        if (!successUrl.contains("{CHECKOUT_SESSION_ID}")) {
            successUrl = successUrl + (successUrl.contains("?") ? "&" : "?") + "session_id={CHECKOUT_SESSION_ID}";
        }

        SessionCreateParams params =
                SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.SETUP)
                        .setCustomer(stripeCustomer.getStripeCustomerExternalId())
                        .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)

                        .setSuccessUrl(successUrl) // ideally append ?session_id={CHECKOUT_SESSION_ID}
                        .setCancelUrl(cancelUrl)

                        // Helpful for mapping back to your tenant/customer
                        .putMetadata("tanso_account_id", accountId)
                        .putMetadata("tanso_customer_id", customerId)
                        .build();

        Session session = stripe.v1().checkout().sessions().create(params);

        StripePaymentLinkDto stripePaymentLinkDto = new StripePaymentLinkDto();
        stripePaymentLinkDto.setPaymentLink(session.getUrl());

        return stripePaymentLinkDto;
    }

    @Override
    public String syncNewPaymentAsDefault(String setupIntentId, String accountId, String stripeCustomerId) throws StripeException {
        StripeClient stripe = stripeClientFactory.forAccount(UUID.fromString(accountId));
        SetupIntent si = stripe.v1().setupIntents().retrieve(setupIntentId);
        String paymentMethodId = si.getPaymentMethod();
        if (paymentMethodId == null) throw new IllegalStateException("Missing payment_method on setup_intent");

        if (stripeCustomerId == null) throw new IllegalStateException("Missing customer on session");

        CustomerUpdateParams update =
                CustomerUpdateParams.builder()
                        .setInvoiceSettings(
                                CustomerUpdateParams.InvoiceSettings.builder()
                                        .setDefaultPaymentMethod(paymentMethodId)
                                        .build()
                        )
                        .build();

        stripe.v1().customers().update(stripeCustomerId, update);
        return paymentMethodId;
    }

    @Override
    public void syncCustomerEmail(UUID accountId, UUID customerId, String email) throws StripeException {
        // Disconnecting Stripe deletes the key but leaves the mirror rows; there is nothing to call then.
        AccountSetting settings = accountService.retrieveAccountSettings(accountId.toString());
        if (settings == null || !settings.isStripeEnabled()) {
            return;
        }
        Customer customer = customerService.validateAndRetrieveCustomer(customerId.toString(), accountId.toString());
        StripeCustomer stripeCustomer = stripeCustomerRepository.findByCustomer(customer);
        if (stripeCustomer == null) {
            return;
        }
        stripeClientFactory.forAccount(accountId).v1().customers().update(
                stripeCustomer.getStripeCustomerExternalId(),
                CustomerUpdateParams.builder().setEmail(email).build());
    }

    @Override
    public boolean stripeInvoiceLinked(String stripeInvoiceId) {
        return stripeInvoiceRepository.existsStripeInvoiceByStripeInvoiceExternalId(stripeInvoiceId);
    }

    @Override
    public void lockStripeInvoice(String stripeInvoiceId) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:k))")
                .setParameter("k", "stripe-invoice:" + stripeInvoiceId)
                .getSingleResult();
    }

    @Override
    public StripeInvoice retrieveStripeInvoiceLinkedData(String stripeInvoiceId) {
        return stripeInvoiceRepository.findStripeInvoiceByStripeInvoiceExternalId(stripeInvoiceId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StripePaymentLinkDto createSubscriptionCheckoutSession(UUID accountId, UUID customerId, UUID planId) throws StripeException {
        StripeClient stripeClient = stripeClientFactory.forAccount(accountId);
        AccountSetting accountSetting = accountService.retrieveAccountSettings(accountId.toString());

        String successUrl = CheckoutReturnUrls.successUrl(accountSetting, CheckoutReturnUrls.KIND_PAYMENT);
        String cancelUrl = CheckoutReturnUrls.cancelUrl(accountSetting);

        if (!successUrl.contains("{CHECKOUT_SESSION_ID}")) {
            successUrl = successUrl + (successUrl.contains("?") ? "&" : "?") + "session_id={CHECKOUT_SESSION_ID}";
        }

        Customer customer = customerService.validateAndRetrieveCustomer(customerId.toString(), accountId.toString());
        StripeCustomer stripeCustomer = stripeCustomerRepository.findByCustomer(customer);
        if (stripeCustomer == null) {
            stripeCustomer = createStripeCustomer(accountId, customerId);
        }

        Plan plan = planService.retrievePlan(UUID.fromString(accountId.toString()), planId);

        // Get or lazily create the Stripe Prices (base + metered where applicable)
        List<StripePrice> stripePrices = stripePriceRepository.findAllByPlanAndAccount(plan, customer.getAccount());
        if (stripePrices.isEmpty()) {
            log.info("No StripePrice for plan {}, creating lazily for checkout", plan.getId());
            createStripeProductWithPrices(planId, accountId);
            stripePrices = stripePriceRepository.findAllByPlanAndAccount(plan, customer.getAccount());
            if (stripePrices.isEmpty()) {
                throw new IllegalStateException("Failed to create StripePrice for plan " + planId);
            }
        }

        Map<String, String> metadata = Map.of(
                "tanso_account_id", accountId.toString(),
                "tanso_customer_id", customerId.toString(),
                "tanso_plan_id", planId.toString()
        );

        SessionCreateParams.SubscriptionData subscriptionData =
                SessionCreateParams.SubscriptionData.builder()
                        .putAllMetadata(metadata)
                        .build();

        SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .putAllMetadata(metadata)
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .setCustomer(stripeCustomer.getStripeCustomerExternalId())
                .setSubscriptionData(subscriptionData);

        // Stripe rejects a quantity on metered prices, and requires one on
        // licensed prices — check each price's usage type before adding it.
        for (StripePrice mapped : stripePrices) {
            Price price = stripeClient.v1().prices().retrieve(mapped.getStripePriceExternalId());
            boolean metered = price.getRecurring() != null
                    && "metered".equals(price.getRecurring().getUsageType());
            SessionCreateParams.LineItem.Builder lineItem = SessionCreateParams.LineItem.builder()
                    .setPrice(mapped.getStripePriceExternalId());
            if (!metered) {
                lineItem.setQuantity(1L);
            }
            paramsBuilder.addLineItem(lineItem.build());
        }

        SessionCreateParams params = paramsBuilder.build();

        Session session = stripeClient.v1().checkout().sessions().create(params);

        StripePaymentLinkDto dto = new StripePaymentLinkDto();
        dto.setPaymentLink(session.getUrl());
        dto.setStripeSessionId(session.getId());
        return dto;
    }

    /**
     * Programmatic path: creates the Stripe subscription directly with a saved
     * payment method — no browser, no Checkout Session. ERROR_IF_INCOMPLETE so
     * a declined or SCA-challenged charge throws instead of leaving a dangling
     * incomplete subscription; the caller maps that to 402.
     */
    /** The Stripe ids a direct subscription needs, read in a short transaction that ends before any charge. */
    private record DirectSubscriptionTarget(String stripeCustomerId, List<String> stripePriceIds) {
    }

    // Not @Transactional: the create below charges the card, and money must not move inside a transaction that can
    // still roll back. The lookups (and the lazy Stripe customer and price set-up, which charge nothing) run in their
    // own short transaction first.
    @Override
    public com.stripe.model.Subscription createDirectSubscription(
            UUID accountId, UUID customerId, UUID planId, String paymentMethodId, UUID pendingChargeId) throws StripeException {
        StripeClient stripeClient = stripeClientFactory.forAccount(accountId);

        DirectSubscriptionTarget target = transactionTemplate.execute(status -> {
            try {
                Customer customer = customerService.validateAndRetrieveCustomer(customerId.toString(), accountId.toString());
                StripeCustomer stripeCustomer = stripeCustomerRepository.findByCustomer(customer);
                if (stripeCustomer == null) {
                    stripeCustomer = createStripeCustomer(accountId, customerId);
                }

                Plan plan = planService.retrievePlan(UUID.fromString(accountId.toString()), planId);
                List<StripePrice> stripePrices = stripePriceRepository.findAllByPlanAndAccount(plan, customer.getAccount());
                if (stripePrices.isEmpty()) {
                    log.info("No StripePrice for plan {}, creating lazily for direct subscription", plan.getId());
                    createStripeProductWithPrices(planId, accountId);
                    stripePrices = stripePriceRepository.findAllByPlanAndAccount(plan, customer.getAccount());
                    if (stripePrices.isEmpty()) {
                        throw new IllegalStateException("Failed to create StripePrice for plan " + planId);
                    }
                }
                return new DirectSubscriptionTarget(stripeCustomer.getStripeCustomerExternalId(),
                        stripePrices.stream().map(StripePrice::getStripePriceExternalId).toList());
            } catch (StripeException e) {
                throw new IllegalStateException("Could not set up Stripe for the subscription of customer " + customerId
                        + " to plan " + planId + ": " + e.getMessage(), e);
            }
        });

        com.stripe.param.SubscriptionCreateParams.Builder builder =
                com.stripe.param.SubscriptionCreateParams.builder()
                        .setCustomer(target.stripeCustomerId())
                        .setDefaultPaymentMethod(paymentMethodId)
                        .setPaymentBehavior(com.stripe.param.SubscriptionCreateParams.PaymentBehavior.ERROR_IF_INCOMPLETE)
                        .setOffSession(true)
                        .putMetadata("tanso_account_id", accountId.toString())
                        .putMetadata("tanso_customer_id", customerId.toString())
                        .putMetadata("tanso_plan_id", planId.toString())
                        // customer.subscription.created uses it to record the spend when the caller could not.
                        .putMetadata(com.tansoflow.tansocore.entity.CheckoutSession.DIRECT_CHARGE_METADATA_KEY,
                                pendingChargeId.toString());

        for (String stripePriceId : target.stripePriceIds()) {
            Price price = stripeClient.v1().prices().retrieve(stripePriceId);
            boolean metered = price.getRecurring() != null
                    && "metered".equals(price.getRecurring().getUsageType());
            com.stripe.param.SubscriptionCreateParams.Item.Builder item =
                    com.stripe.param.SubscriptionCreateParams.Item.builder()
                            .setPrice(stripePriceId);
            if (!metered) {
                item.setQuantity(1L);
            }
            builder.addItem(item.build());
        }

        // One key per pending charge: a retry after a lost response, or after Tanso failed to record the result,
        // gets Stripe's first subscription back (for 24 hours) instead of a second one and a second charge.
        com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                .setIdempotencyKey("tanso-subscribe-" + pendingChargeId)
                .build();
        return stripeClient.v1().subscriptions().create(builder.build(), requestOptions);
    }

    // ── STRIPE_INTEGRATION Methods ─────────────────────────────────────────────

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createStripeProductWithPrices(UUID planId, UUID accountId) throws StripeException {
        StripeClient stripeClient = stripeClientFactory.forAccount(accountId);
        Plan plan = planService.retrievePlan(UUID.fromString(accountId.toString()), planId);

        // Ensure product exists
        StripeProduct stripeProduct = stripeProductPlansRepository.findStripeProductByPlan(plan);
        if (stripeProduct == null) {
            Product product = createStripeProduct(planId.toString(), accountId.toString());
            stripeProduct = stripeProductPlansRepository.findStripeProductByPlan(plan);
        }

        String productId = stripeProduct.getStripeProductExternalId();

        // Check for usage-based features to determine pricing type
        List<PlanFeatureRule> rules = planFeatureRuleRepository.findPlanFeatureRulesByPlanId(planId);

        boolean hasGraduatedPricing = false;
        boolean hasUsagePricing = false;
        GraduatedPricingModel graduatedModel = null;
        SimpleUsageModel usageModel = null;
        PlanFeatureRule meteredRule = null;

        for (PlanFeatureRule rule : rules) {
            PricingModel pm = RuleCalculationUtil.extractPricingModel(rule);
            if (pm instanceof GraduatedPricingModel gpm) {
                hasGraduatedPricing = true;
                graduatedModel = gpm;
                meteredRule = rule;
                break;
            } else if (pm instanceof SimpleUsageModel sum) {
                hasUsagePricing = true;
                usageModel = sum;
                meteredRule = rule;
            }
        }

        Price stripePrice;

        // For metered prices, ensure a Stripe Meter exists for the feature
        String meterId = null;
        if (meteredRule != null) {
            Feature meteredFeature = meteredRule.getFeature();
            StripeMeter stripeMeter = stripeMeterRepository
                    .findByFeatureAndAccount(meteredFeature, plan.getAccount())
                    .orElse(null);
            if (stripeMeter == null) {
                createStripeMeter(meteredFeature.getId(), plan, accountId);
                stripeMeter = stripeMeterRepository
                        .findByFeatureAndAccount(meteredFeature, plan.getAccount())
                        .orElseThrow();
            }
            meterId = stripeMeter.getStripeMeterExternalId();
        }

        if (hasGraduatedPricing) {
            // Create tiered pricing in Stripe
            List<PriceCreateParams.Tier> stripeTiers = new ArrayList<>();

            for (GraduatedPricingModel.PriceTier tier : graduatedModel.getTiers()) {
                PriceCreateParams.Tier.Builder tierBuilder = PriceCreateParams.Tier.builder()
                        .setUnitAmount(tier.getPricePerUnit().movePointRight(2).longValueExact());

                if (tier.getFlatFee() != null && tier.getFlatFee().compareTo(BigDecimal.ZERO) > 0) {
                    tierBuilder.setFlatAmount(tier.getFlatFee().movePointRight(2).longValueExact());
                }

                if (tier.getUpTo() == null || "inf".equalsIgnoreCase(tier.getUpTo().toString())) {
                    tierBuilder.setUpTo(PriceCreateParams.Tier.UpTo.INF);
                } else {
                    tierBuilder.setUpTo(Long.parseLong(tier.getUpTo().toString()));
                }

                stripeTiers.add(tierBuilder.build());
            }

            PriceCreateParams priceParams = PriceCreateParams.builder()
                    .setCurrency("usd")
                    .setProduct(productId)
                    .setBillingScheme(PriceCreateParams.BillingScheme.TIERED)
                    .setTiersMode(PriceCreateParams.TiersMode.GRADUATED)
                    .addAllTier(stripeTiers)
                    .setRecurring(
                            PriceCreateParams.Recurring.builder()
                                    .setInterval(PriceCreateParams.Recurring.Interval.MONTH)
                                    .setIntervalCount(plan.getIntervalMonths().longValue())
                                    .setUsageType(PriceCreateParams.Recurring.UsageType.METERED)
                                    .setMeter(meterId)
                                    .build()
                    )
                    .build();

            stripePrice = stripeClient.v1().prices().create(priceParams);

        } else if (hasUsagePricing) {
            // Per-unit metered pricing
            long amountInCents = usageModel.getRate().movePointRight(2).longValueExact();

            PriceCreateParams priceParams = PriceCreateParams.builder()
                    .setCurrency("usd")
                    .setProduct(productId)
                    .setUnitAmount(amountInCents)
                    .setRecurring(
                            PriceCreateParams.Recurring.builder()
                                    .setInterval(PriceCreateParams.Recurring.Interval.MONTH)
                                    .setIntervalCount(plan.getIntervalMonths().longValue())
                                    .setUsageType(PriceCreateParams.Recurring.UsageType.METERED)
                                    .setMeter(meterId)
                                    .build()
                    )
                    .build();

            stripePrice = stripeClient.v1().prices().create(priceParams);

        } else {
            // Flat-rate recurring pricing (standard plan price)
            stripePrice = createStripePrice(plan, productId);
        }

        // Save the StripePrice mapping
        StripePrice stripePriceEntity = new StripePrice();
        stripePriceEntity.setPlan(plan);
        stripePriceEntity.setStripeProduct(stripeProduct);
        stripePriceEntity.setAccount(plan.getAccount());
        stripePriceEntity.setStripePriceExternalId(stripePrice.getId());
        stripePriceRepository.save(stripePriceEntity);

        // A metered price only covers usage — a plan with a base price needs the
        // flat recurring price too, or checkout silently drops the subscription fee.
        if (meteredRule != null && plan.getPriceAmount() != null
                && plan.getPriceAmount().compareTo(BigDecimal.ZERO) > 0) {
            Price basePrice = createStripePrice(plan, productId);
            StripePrice basePriceEntity = new StripePrice();
            basePriceEntity.setPlan(plan);
            basePriceEntity.setStripeProduct(stripeProduct);
            basePriceEntity.setAccount(plan.getAccount());
            basePriceEntity.setStripePriceExternalId(basePrice.getId());
            stripePriceRepository.save(basePriceEntity);
        }

        log.info("Created Stripe Product+Price for plan {} in account {}. Stripe Price ID: {}",
                planId, accountId, stripePrice.getId());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createStripeSubscription(UUID subscriptionId, UUID accountId) throws StripeException {
        StripeClient stripeClient = stripeClientFactory.forAccount(accountId);

        Subscription subscription = subscriptionRepository.findSubscriptionByUuidAndAccountId(subscriptionId, accountId);
        if (subscription == null) {
            throw new IllegalArgumentException("Subscription not found: " + subscriptionId);
        }

        // Check if already linked
        if (stripeSubscriptionRepository.existsStripeSubscriptionBySubscription(subscription)) {
            log.info("Subscription {} already linked to Stripe, skipping", subscriptionId);
            return;
        }

        // Get Stripe customer
        StripeCustomer stripeCustomer = stripeCustomerRepository.findByCustomer(subscription.getCustomer());
        if (stripeCustomer == null) {
            stripeCustomer = createStripeCustomer(accountId, subscription.getCustomer().getId());
        }

        // Every price of the plan, newest of each usage type (lazily created if the plan predates the Stripe
        // setup). A plan with a usage-priced feature has a metered price and, when it costs money, a licensed base
        // price. Only the newest one used to go on the subscription, which is the base price, so Stripe never
        // billed the plan's usage.
        Map<String, String> planPrices = newestPriceByUsageType(stripeClient,
                stripePriceIdsFor(subscription.getPlan(), subscription.getAccount(), accountId));

        com.stripe.model.Subscription stripeSubscription;
        try {
            stripeSubscription = stripeClient.v1().subscriptions()
                    .create(stripeSubscriptionParams(subscription, stripeCustomer, planPrices, accountId));
        } catch (InvalidRequestException e) {
            if ("resource_missing".equals(e.getCode()) && e.getMessage() != null && e.getMessage().contains("price")) {
                // Stale StripePrice rows (the prices are gone from this Stripe account): delete, recreate, retry
                log.warn("Stale StripePrice {} for plan {}, recreating", planPrices.values(), subscription.getPlan().getId());
                stripePriceRepository.deleteAll(stripePriceRepository
                        .findAllByPlanAndAccountOrderByCreatedAtDesc(subscription.getPlan(), subscription.getAccount()));
                Map<String, String> freshPrices = newestPriceByUsageType(stripeClient,
                        stripePriceIdsFor(subscription.getPlan(), subscription.getAccount(), accountId));
                stripeSubscription = stripeClient.v1().subscriptions()
                        .create(stripeSubscriptionParams(subscription, stripeCustomer, freshPrices, accountId));
            } else {
                throw e;
            }
        }

        // Save mapping
        StripeSubscription stripeSubEntity = new StripeSubscription();
        stripeSubEntity.setSubscription(subscription);
        stripeSubEntity.setAccount(subscription.getAccount());
        stripeSubEntity.setStripeSubscriptionExternalId(stripeSubscription.getId());
        stripeSubscriptionRepository.save(stripeSubEntity);

        // Link the auto-generated Stripe invoice to the existing Tanso invoice
        String stripeInvoiceId = stripeSubscription.getLatestInvoice();
        if (stripeInvoiceId != null
                && !stripeInvoiceRepository.existsStripeInvoiceByStripeInvoiceExternalId(stripeInvoiceId)) {
            List<Invoice> outstanding = invoiceRepository.findOutstandingInvoicesBySubscription(subscription);
            if (!outstanding.isEmpty()) {
                StripeInvoice stripeInvoiceLink = new StripeInvoice();
                stripeInvoiceLink.setInvoice(outstanding.getFirst());
                stripeInvoiceLink.setStripeInvoiceExternalId(stripeInvoiceId);
                stripeInvoiceRepository.save(stripeInvoiceLink);
                log.info("Linked Stripe auto-invoice {} to Tanso invoice {}",
                        stripeInvoiceId, outstanding.getFirst().getId());
            }
        }

        log.info("Created Stripe Subscription {} for Tanso subscription {} in account {}",
                stripeSubscription.getId(), subscriptionId, accountId);
    }

    /** One item per plan price; a metered item takes no quantity. */
    private SubscriptionCreateParams stripeSubscriptionParams(Subscription subscription, StripeCustomer stripeCustomer,
                                                              Map<String, String> planPriceByUsageType, UUID accountId) {
        SubscriptionCreateParams.Builder subParamsBuilder = SubscriptionCreateParams.builder()
                .setCustomer(stripeCustomer.getStripeCustomerExternalId())
                .putMetadata("tanso_account_id", accountId.toString())
                .putMetadata("tanso_subscription_id", subscription.getId().toString())
                .putMetadata("tanso_customer_id", subscription.getCustomer().getId().toString())
                .setProrationBehavior(SubscriptionCreateParams.ProrationBehavior.NONE);
        planPriceByUsageType.forEach((usageType, priceId) -> {
            SubscriptionCreateParams.Item.Builder item = SubscriptionCreateParams.Item.builder().setPrice(priceId);
            if (!"metered".equals(usageType)) {
                item.setQuantity(1L);
            }
            subParamsBuilder.addItem(item.build());
        });

        // For accumulate-mode plans, use send_invoice so Stripe never auto-charges
        // tanso-core will calculate the correct amount and pay the invoice programmatically
        if (invoiceService.planHasAccumulateModeFeatures(subscription.getPlan())) {
            subParamsBuilder
                    .setCollectionMethod(SubscriptionCreateParams.CollectionMethod.SEND_INVOICE)
                    .setDaysUntilDue(1L);
        }

        // For IN_ADVANCE plans that haven't been paid yet: create the subscription + invoice
        // but leave it incomplete until the customer pays (prevents auto-charging without consent)
        if (BillingTiming.IN_ADVANCE.name().equals(subscription.getPlan().getBillingTiming())
                && !subscription.getIsActive()) {
            subParamsBuilder.setPaymentBehavior(
                    SubscriptionCreateParams.PaymentBehavior.DEFAULT_INCOMPLETE);
        }
        return subParamsBuilder.build();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStripeSubscriptionPrice(UUID subscriptionId, UUID accountId, UUID planId, boolean prorate) throws StripeException {
        StripeClient stripeClient = stripeClientFactory.forAccount(accountId);

        Subscription subscription = subscriptionRepository.findSubscriptionByUuidAndAccountId(subscriptionId, accountId);
        if (subscription == null) {
            log.warn("Subscription not found for plan change: {}", subscriptionId);
            return;
        }

        StripeSubscription stripeSub = stripeSubscriptionRepository.findStripeSubscriptionBySubscription(subscription);
        if (stripeSub == null) {
            log.warn("No Stripe subscription linked for Tanso subscription {}, skipping plan change sync", subscriptionId);
            return;
        }

        // The plan to price against comes from the caller, not subscription.getPlan(): a STRIPE_INTEGRATION upgrade
        // leaves the Tanso subscription on its old plan until payment, and reading it here re-sent the old price.
        Plan plan = planService.retrievePlan(subscription.getAccount(), planId);
        List<String> planPriceIds = stripePriceIdsFor(plan, subscription.getAccount(), accountId);

        com.stripe.model.Subscription currentStripeSub = stripeClient.v1().subscriptions()
                .retrieve(stripeSub.getStripeSubscriptionExternalId());

        SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                .addAllItem(itemsMovingTo(currentStripeSub, newestPriceByUsageType(stripeClient, planPriceIds)))
                .setProrationBehavior(prorate
                        ? SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS
                        : SubscriptionUpdateParams.ProrationBehavior.NONE)
                .build();

        stripeClient.v1().subscriptions().update(stripeSub.getStripeSubscriptionExternalId(), params);

        log.info("Updated Stripe subscription {} to the prices {} of plan {} (prorate={})",
                stripeSub.getStripeSubscriptionExternalId(), planPriceIds, plan.getId(), prorate);
    }

    /** The Stripe ids a charge-first upgrade needs, read in a short transaction that ends before any charge. */
    private record UpgradeChargeTarget(String stripeSubscriptionId, List<String> stripePriceIds) {
    }

    // Not @Transactional: the update below can charge a card, and money must not move inside a transaction that can
    // still roll back. The lookups run in their own short transaction first.
    @Override
    public StripeUpgradeCharge chargeUpgradeBeforeApplying(UUID subscriptionId, UUID accountId, UUID planId,
                                                           UUID scheduledChangeId) throws StripeException {
        StripeClient stripeClient = stripeClientFactory.forAccount(accountId);

        UpgradeChargeTarget target = transactionTemplate.execute(status -> {
            Subscription subscription = subscriptionRepository.findSubscriptionByUuidAndAccountId(subscriptionId, accountId);
            if (subscription == null) {
                throw new IllegalArgumentException("Subscription not found for plan change: " + subscriptionId);
            }
            // Without a Stripe subscription there is nothing for Stripe to charge, and swapping the plan anyway would
            // hand an agent a paid tier nobody paid for.
            StripeSubscription linked = stripeSubscriptionRepository.findStripeSubscriptionBySubscription(subscription);
            if (linked == null) {
                throw new IllegalStateException("Subscription " + subscriptionId
                        + " has no linked Stripe subscription; the upgrade cannot be charged");
            }
            Plan plan = planService.retrievePlan(subscription.getAccount(), planId);
            try {
                return new UpgradeChargeTarget(linked.getStripeSubscriptionExternalId(),
                        stripePriceIdsFor(plan, subscription.getAccount(), accountId));
            } catch (StripeException e) {
                throw new IllegalStateException("Could not set up the Stripe price of plan " + planId
                        + " for the upgrade of subscription " + subscriptionId + ": " + e.getMessage(), e);
            }
        });

        com.stripe.model.Subscription currentStripeSub = stripeClient.v1().subscriptions()
                .retrieve(target.stripeSubscriptionId());
        Map<String, String> newPrices = newestPriceByUsageType(stripeClient, target.stripePriceIds());
        // Accumulate-mode plans create send_invoice subscriptions. Stripe only supports pending updates on
        // charge_automatically subscriptions (docs.stripe.com/billing/subscriptions/pending-updates).
        boolean sendInvoice = "send_invoice".equals(currentStripeSub.getCollectionMethod());

        // always_invoice raises and charges the proration now instead of adding it to the next renewal.
        // pending_if_incomplete makes Stripe apply the new price only if that charge succeeds; otherwise the
        // subscription keeps its old price and carries a pending_update until the invoice is paid or expires.
        // On send_invoice Stripe moves the price and sends the invoice; Tanso still waits for it to be paid.
        // A plan with a usage-priced feature has a metered price and, when it costs money, a licensed base price;
        // each existing item moves to the new plan's price of its own usage type. Swapping only the first item
        // asked Stripe to turn a metered item into a licensed one, which it refuses, so the upgrade failed.
        SubscriptionUpdateParams.Builder paramsBuilder = SubscriptionUpdateParams.builder()
                .addAllItem(itemsMovingTo(currentStripeSub, newPrices))
                .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.ALWAYS_INVOICE)
                .addExpand("latest_invoice");
        if (!sendInvoice) {
            paramsBuilder.setPaymentBehavior(SubscriptionUpdateParams.PaymentBehavior.PENDING_IF_INCOMPLETE);
        }

        // One key per Tanso scheduled change: a retry after a lost response, or after Tanso failed to record the
        // answer, gets Stripe's first answer back (for 24 hours) instead of a second charge.
        com.stripe.net.RequestOptions requestOptions = com.stripe.net.RequestOptions.builder()
                .setIdempotencyKey(upgradeIdempotencyKey(scheduledChangeId))
                .build();
        com.stripe.model.Subscription updated;
        com.stripe.model.Invoice invoice;
        if (currentStripeSub.getPendingUpdate() == null && onPrices(currentStripeSub, newPrices.values())) {
            // Already on the new prices: an earlier call for this change went through and its reply was lost. The
            // items differ from that call's now, so repeating it under the same key would be refused as a different
            // request. Its invoice is the subscription's latest.
            updated = currentStripeSub;
            invoice = stripeClient.v1().invoices().retrieve(currentStripeSub.getLatestInvoice());
        } else {
            updated = stripeClient.v1().subscriptions()
                    .update(target.stripeSubscriptionId(), paramsBuilder.build(), requestOptions);
            invoice = updated.getLatestInvoiceObject();
        }
        if (invoice == null) {
            throw new IllegalStateException("Stripe returned no invoice for the upgrade of subscription "
                    + target.stripeSubscriptionId());
        }
        // A draft has no hosted page yet, and the human needs one to pay.
        if (sendInvoice && "draft".equals(invoice.getStatus())) {
            // Keyed too: a retry replays the update's draft reply, and finalizing it a second time would fail.
            invoice = stripeClient.v1().invoices().finalizeInvoice(invoice.getId(), com.stripe.net.RequestOptions.builder()
                    .setIdempotencyKey(upgradeIdempotencyKey(scheduledChangeId) + "-finalize")
                    .build());
        }
        // The plan moves only when Stripe holds the new price AND the money. On send_invoice the first is true at
        // once, so the invoice status is what decides.
        boolean applied = updated.getPendingUpdate() == null && "paid".equals(invoice.getStatus());
        if (!applied && invoice.getHostedInvoiceUrl() == null) {
            throw new IllegalStateException("Stripe could not charge the upgrade of subscription "
                    + target.stripeSubscriptionId() + " and returned no hosted invoice " + invoice.getId());
        }
        BigDecimal amountPaid = invoice.getAmountPaid() != null
                ? BigDecimal.valueOf(invoice.getAmountPaid()).movePointLeft(2)
                : BigDecimal.ZERO;

        log.info("Charge-first upgrade of Stripe subscription {} to prices {} for plan {} (scheduled change {}): invoice {} applied={} sendInvoice={}",
                target.stripeSubscriptionId(), newPrices.values(), planId, scheduledChangeId,
                invoice.getId(), applied, sendInvoice);
        return new StripeUpgradeCharge(invoice.getId(), invoice.getHostedInvoiceUrl(), amountPaid, applied);
    }

    static String upgradeIdempotencyKey(UUID scheduledChangeId) {
        return "tanso-upgrade-" + scheduledChangeId;
    }

    @Override
    public void voidStripeInvoice(String stripeInvoiceId, UUID accountId) throws StripeException {
        StripeClient stripeClient = stripeClientFactory.forAccount(accountId);
        stripeClient.v1().invoices().voidInvoice(stripeInvoiceId);
        log.info("Voided Stripe invoice {} for account {}", stripeInvoiceId, accountId);
    }

    @Override
    @Transactional
    public void cancelUnpaidUpgrade(String stripeInvoiceId, UUID subscriptionId, UUID accountId) throws StripeException {
        StripeClient stripeClient = stripeClientFactory.forAccount(accountId);
        // On charge_automatically this also discards the pending_update, and the old price never left.
        stripeClient.v1().invoices().voidInvoice(stripeInvoiceId);
        log.info("Voided Stripe invoice {} behind an unpaid upgrade of subscription {}", stripeInvoiceId, subscriptionId);
        restorePriceAfterDroppedUpgrade(subscriptionId, accountId);
    }

    @Override
    @Transactional
    public void restorePriceAfterDroppedUpgrade(UUID subscriptionId, UUID accountId) throws StripeException {
        StripeClient stripeClient = stripeClientFactory.forAccount(accountId);
        Subscription subscription = subscriptionRepository.findSubscriptionByUuidAndAccountId(subscriptionId, accountId);
        StripeSubscription stripeSub = subscription == null ? null
                : stripeSubscriptionRepository.findStripeSubscriptionBySubscription(subscription);
        if (stripeSub == null) {
            throw new IllegalStateException("Subscription " + subscriptionId
                    + " has no linked Stripe subscription; cannot restore its price after dropping the upgrade");
        }
        com.stripe.model.Subscription currentStripeSub = stripeClient.v1().subscriptions()
                .retrieve(stripeSub.getStripeSubscriptionExternalId());
        if (!"send_invoice".equals(currentStripeSub.getCollectionMethod())) {
            return;
        }

        // send_invoice: Stripe moved to the new price when it raised the invoice. Left there, the next renewal would
        // bill a plan Tanso never granted.
        List<String> planPriceIds = stripePriceIdsFor(subscription.getPlan(), subscription.getAccount(), accountId);
        SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                .addAllItem(itemsMovingTo(currentStripeSub, newestPriceByUsageType(stripeClient, planPriceIds)))
                .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.NONE)
                .build();
        stripeClient.v1().subscriptions().update(stripeSub.getStripeSubscriptionExternalId(), params);
        log.info("Restored Stripe subscription {} to the prices {} of plan {} after dropping an unpaid upgrade",
                stripeSub.getStripeSubscriptionExternalId(), planPriceIds, subscription.getPlan().getId());
    }

    /** A plan's Stripe price ids, newest first, creating the product and prices first if the plan was never synced. */
    private List<String> stripePriceIdsFor(Plan plan, Account account, UUID accountId) throws StripeException {
        List<StripePrice> prices = stripePriceRepository.findAllByPlanAndAccountOrderByCreatedAtDesc(plan, account);
        if (prices.isEmpty()) {
            log.info("No StripePrice for plan {}, creating lazily", plan.getId());
            createStripeProductWithPrices(plan.getId(), accountId);
            prices = stripePriceRepository.findAllByPlanAndAccountOrderByCreatedAtDesc(plan, account);
            if (prices.isEmpty()) {
                throw new IllegalStateException("Failed to create StripePrice for plan " + plan.getId());
            }
        }
        return prices.stream().map(StripePrice::getStripePriceExternalId).toList();
    }

    /** The newest price of each usage type ("metered", "licensed") among a plan's prices, given newest first. */
    private Map<String, String> newestPriceByUsageType(StripeClient stripeClient, List<String> priceIdsNewestFirst)
            throws StripeException {
        Map<String, String> byUsageType = new java.util.LinkedHashMap<>();
        for (String priceId : priceIdsNewestFirst) {
            byUsageType.putIfAbsent(usageTypeOf(stripeClient.v1().prices().retrieve(priceId)), priceId);
        }
        return byUsageType;
    }

    /**
     * The item changes that put a Stripe subscription on a plan's prices: each existing item moves to the new price
     * of its usage type, an item whose type the plan does not have is removed, and a price with no item to move
     * is added. Stripe does not let an item change between metered and licensed.
     */
    private static List<SubscriptionUpdateParams.Item> itemsMovingTo(com.stripe.model.Subscription current,
                                                             Map<String, String> newPriceByUsageType) {
        Map<String, String> unplaced = new java.util.LinkedHashMap<>(newPriceByUsageType);
        List<SubscriptionUpdateParams.Item> items = new ArrayList<>();
        for (com.stripe.model.SubscriptionItem existing : current.getItems().getData()) {
            String newPrice = unplaced.remove(usageTypeOf(existing.getPrice()));
            if (newPrice != null) {
                items.add(SubscriptionUpdateParams.Item.builder().setId(existing.getId()).setPrice(newPrice).build());
            } else {
                items.add(SubscriptionUpdateParams.Item.builder().setId(existing.getId()).setDeleted(true).build());
            }
        }
        unplaced.forEach((usageType, priceId) -> {
            SubscriptionUpdateParams.Item.Builder added = SubscriptionUpdateParams.Item.builder().setPrice(priceId);
            if (!"metered".equals(usageType)) {
                added.setQuantity(1L);
            }
            items.add(added.build());
        });
        return items;
    }

    /** Whether the subscription's items carry exactly these prices. */
    private static boolean onPrices(com.stripe.model.Subscription current, java.util.Collection<String> priceIds) {
        java.util.Set<String> live = new java.util.HashSet<>();
        for (com.stripe.model.SubscriptionItem item : current.getItems().getData()) {
            if (item.getPrice() == null) {
                return false;
            }
            live.add(item.getPrice().getId());
        }
        return live.equals(new java.util.HashSet<>(priceIds));
    }

    private static String usageTypeOf(Price price) {
        return price != null && price.getRecurring() != null && "metered".equals(price.getRecurring().getUsageType())
                ? "metered" : "licensed";
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelStripeSubscription(UUID subscriptionId, UUID accountId, String cancelMode) throws StripeException {
        StripeClient stripeClient = stripeClientFactory.forAccount(accountId);

        Subscription subscription = subscriptionRepository.findSubscriptionByUuidAndAccountId(subscriptionId, accountId);
        if (subscription == null) {
            log.warn("Subscription not found for cancel: {}", subscriptionId);
            return;
        }

        StripeSubscription stripeSub = stripeSubscriptionRepository.findStripeSubscriptionBySubscription(subscription);
        if (stripeSub == null) {
            log.warn("No Stripe subscription linked for Tanso subscription {}", subscriptionId);
            return;
        }

        String stripeSubId = stripeSub.getStripeSubscriptionExternalId();

        if ("END_OF_PERIOD".equals(cancelMode)) {
            // Schedule cancellation at the end of the current billing period
            stripeClient.v1().subscriptions().update(stripeSubId,
                    SubscriptionUpdateParams.builder()
                            .setCancelAtPeriodEnd(true)
                            .build());
            log.info("Scheduled Stripe Subscription {} for cancel at period end for Tanso subscription {}",
                    stripeSubId, subscriptionId);
        } else {
            // Immediate cancel
            stripeClient.v1().subscriptions().cancel(stripeSubId,
                    SubscriptionCancelParams.builder().build());
            log.info("Immediately cancelled Stripe Subscription {} for Tanso subscription {}",
                    stripeSubId, subscriptionId);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createStripeMeter(UUID featureId, Plan plan, UUID accountId) throws StripeException {
        StripeClient stripeClient = stripeClientFactory.forAccount(accountId);

        Feature feature = featureRepository.findById(featureId)
                .orElseThrow(() -> new IllegalArgumentException("Feature not found: " + featureId));

        // Check if meter already exists
        if (stripeMeterRepository.findByFeatureAndAccount(feature, feature.getAccount()).isPresent()) {
            log.info("Stripe meter already exists for feature {} in account {}", featureId, accountId);
            return;
        }

        String eventName = "tanso_" + feature.getKey() + plan.getKey();

        MeterCreateParams params = MeterCreateParams.builder()
                .setDisplayName(feature.getName())
                .setEventName(eventName)
                .setDefaultAggregation(
                        MeterCreateParams.DefaultAggregation.builder()
                                .setFormula(MeterCreateParams.DefaultAggregation.Formula.SUM)
                                .build()
                )
                .setCustomerMapping(
                        MeterCreateParams.CustomerMapping.builder()
                                .setType(MeterCreateParams.CustomerMapping.Type.BY_ID)
                                .setEventPayloadKey("stripe_customer_id")
                                .build()
                )
                .build();

        com.stripe.model.billing.Meter meter = stripeClient.v1().billing().meters().create(params);

        StripeMeter stripeMeter = new StripeMeter();
        stripeMeter.setFeature(feature);
        stripeMeter.setAccount(feature.getAccount());
        stripeMeter.setStripeMeterExternalId(meter.getId());
        stripeMeter.setStripeMeterEventName(eventName);
        stripeMeterRepository.save(stripeMeter);

        log.info("Created Stripe Meter {} for feature {} in account {}", meter.getId(), featureId, accountId);
    }

    @Override
    public void forwardUsageToStripeMeter(UUID eventFeatureId, UUID customerId, UUID accountId, BigDecimal usageUnits, Instant timestamp) throws StripeException {
        StripeClient stripeClient = stripeClientFactory.forAccount(accountId);

        Feature feature = featureRepository.findById(eventFeatureId)
                .orElseThrow(() -> new IllegalArgumentException("Feature not found: " + eventFeatureId));

        StripeMeter stripeMeter = stripeMeterRepository.findByFeatureAndAccount(feature, feature.getAccount())
                .orElse(null);

        if (stripeMeter == null) {
            log.debug("No Stripe meter for feature {}, skipping usage forwarding", eventFeatureId);
            return;
        }

        Customer customer = customerService.validateAndRetrieveCustomer(customerId.toString(), accountId.toString());
        StripeCustomer stripeCustomer = stripeCustomerRepository.findByCustomer(customer);
        if (stripeCustomer == null) {
            log.warn("No Stripe customer for Tanso customer {}, skipping usage forwarding", customerId);
            return;
        }

        MeterEventCreateParams params = MeterEventCreateParams.builder()
                .setEventName(stripeMeter.getStripeMeterEventName())
                .putPayload("stripe_customer_id", stripeCustomer.getStripeCustomerExternalId())
                .putPayload("value", usageUnits.toPlainString())
                .setTimestamp(timestamp.getEpochSecond())
                .build();

        stripeClient.v1().billing().meterEvents().create(params);

        log.debug("Forwarded {} usage units for feature {} to Stripe meter {}", usageUnits, eventFeatureId, stripeMeter.getStripeMeterExternalId());
    }

    @Override
    public void disableAutoAdvanceOnStripeInvoice(String stripeInvoiceId, UUID accountId) throws StripeException {
        StripeClient stripeClient = stripeClientFactory.forAccount(accountId);

        com.stripe.param.InvoiceUpdateParams updateParams = com.stripe.param.InvoiceUpdateParams.builder()
                .setAutoAdvance(false)
                .build();

        stripeClient.v1().invoices().update(stripeInvoiceId, updateParams);
        log.info("Disabled auto_advance on Stripe invoice {} for account {}", stripeInvoiceId, accountId);
    }

    @Override
    public void voidStripeInvoiceFor(UUID tansoInvoiceId, UUID accountId) throws StripeException {
        com.tansoflow.tansocore.entity.StripeInvoice mirrored = stripeInvoiceRepository.findByTansoInvoiceId(tansoInvoiceId);
        if (mirrored == null || mirrored.getStripeInvoiceExternalId() == null) {
            return;
        }
        StripeClient stripeClient = stripeClientFactory.forAccount(accountId);
        stripeClient.v1().invoices().voidInvoice(mirrored.getStripeInvoiceExternalId());
        log.info("Voided Stripe invoice {} for Tanso invoice {}", mirrored.getStripeInvoiceExternalId(), tansoInvoiceId);
    }

    @Override
    public void addLineItemToDraftInvoice(String stripeInvoiceId, UUID accountId, BigDecimal amount, String currency, String description) throws StripeException {
        StripeClient stripeClient = stripeClientFactory.forAccount(accountId);

        // Resolve Stripe customer from the invoice
        com.stripe.model.Invoice stripeInvoice = stripeClient.v1().invoices().retrieve(stripeInvoiceId);

        InvoiceItemCreateParams itemParams = InvoiceItemCreateParams.builder()
                .setCustomer(stripeInvoice.getCustomer())
                .setInvoice(stripeInvoiceId)
                .setAmount(amount.movePointRight(2).longValueExact())
                .setCurrency(currency != null ? currency.toLowerCase() : "usd")
                .setDescription(description)
                .putMetadata("tanso_accumulate_charge", "true")
                .build();

        stripeClient.v1().invoiceItems().create(itemParams);
        log.info("Added line item of {} {} to Stripe draft invoice {} for account {}", amount, currency, stripeInvoiceId, accountId);
    }

    @Override
    public void finalizeAndPayStripeInvoice(String stripeInvoiceId, UUID accountId) throws StripeException {
        StripeClient stripeClient = stripeClientFactory.forAccount(accountId);

        stripeClient.v1().invoices().finalizeInvoice(stripeInvoiceId);
        log.info("Finalized Stripe invoice {} for account {}", stripeInvoiceId, accountId);

        stripeClient.v1().invoices().pay(stripeInvoiceId);
        log.info("Paid Stripe invoice {} for account {}", stripeInvoiceId, accountId);
    }
}
