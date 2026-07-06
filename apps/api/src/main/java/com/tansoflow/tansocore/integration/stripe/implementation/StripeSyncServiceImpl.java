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
import com.tansoflow.tansocore.integration.stripe.StripeClientFactory;
import com.tansoflow.tansocore.integration.stripe.StripeSyncService;
import com.tansoflow.tansocore.model.data.stripe.StripePaymentLinkDto;
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

            CustomerCreateParams params = CustomerCreateParams.builder()
                    .setName(customer.getFirstName() + " " + customer.getLastName())
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
                    .setAmount(toStripeMinorUnits(invoice.getAmount(), currency))
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
                        .setAmount(toStripeMinorUnits(item.getChargeAmount(), currency))
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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


        String successUrl = accountSetting != null ? accountSetting.getStripeCheckoutSuccessUrl() : "https://example.com/success";
        String cancelUrl = accountSetting != null ? accountSetting.getStripeCheckoutCancelUrl() : "https://example.com/cancel";

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

        String currency = plan.getCurrency() == null ? "usd" : plan.getCurrency().toLowerCase();
        BigDecimal amount = plan.getPriceAmount();
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("Price amount cannot have more than 2 decimal places");
        }
        long amountInCents = toStripeMinorUnits(amount, currency);

        PriceCreateParams.Builder priceParamsBuilder = PriceCreateParams.builder()
                .setCurrency(currency)
                .setProduct(productId)
                .setUnitAmount(amountInCents);// Convert to smallest currency unit

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

        String successUrl = accountSetting != null ? accountSetting.getStripeCheckoutSuccessUrl() : "https://example.com/success";
        String cancelUrl = accountSetting != null ? accountSetting.getStripeCheckoutCancelUrl() : "https://example.com/cancel";

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
    public void syncNewPaymentAsDefault(String setupIntentId, String accountId, String stripeCustomerId) throws StripeException {
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
    }

    @Override
    public boolean stripeInvoiceLinked(String stripeInvoiceId) {
        return stripeInvoiceRepository.existsStripeInvoiceByStripeInvoiceExternalId(stripeInvoiceId);
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

        String successUrl = accountSetting != null ? accountSetting.getStripeCheckoutSuccessUrl() : "https://example.com/success";
        String cancelUrl = accountSetting != null ? accountSetting.getStripeCheckoutCancelUrl() : "https://example.com/cancel";

        if (!successUrl.contains("{CHECKOUT_SESSION_ID}")) {
            successUrl = successUrl + (successUrl.contains("?") ? "&" : "?") + "session_id={CHECKOUT_SESSION_ID}";
        }

        Customer customer = customerService.validateAndRetrieveCustomer(customerId.toString(), accountId.toString());
        StripeCustomer stripeCustomer = stripeCustomerRepository.findByCustomer(customer);
        if (stripeCustomer == null) {
            stripeCustomer = createStripeCustomer(accountId, customerId);
        }

        Plan plan = planService.retrievePlan(UUID.fromString(accountId.toString()), planId);

        // Get or lazily create the Stripe Price
        StripePrice stripePrice = stripePriceRepository
                .findFirstByPlanAndAccountOrderByCreatedAtDesc(plan, customer.getAccount())
                .orElse(null);
        if (stripePrice == null) {
            log.info("No StripePrice for plan {}, creating lazily for checkout", plan.getId());
            createStripeProductWithPrices(planId, accountId);
            stripePrice = stripePriceRepository
                    .findFirstByPlanAndAccountOrderByCreatedAtDesc(plan, customer.getAccount())
                    .orElseThrow(() -> new IllegalStateException("Failed to create StripePrice for plan " + planId));
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

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(stripePrice.getStripePriceExternalId())
                        .setQuantity(1L)
                        .build())
                .putAllMetadata(metadata)
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .setCustomer(stripeCustomer.getStripeCustomerExternalId())
                .setSubscriptionData(subscriptionData)
                .build();

        Session session = stripeClient.v1().checkout().sessions().create(params);

        StripePaymentLinkDto dto = new StripePaymentLinkDto();
        dto.setPaymentLink(session.getUrl());
        return dto;
    }

    // ── STRIPE_INTEGRATION Methods ─────────────────────────────────────────────

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createStripeProductWithPrices(UUID planId, UUID accountId) throws StripeException {
        StripeClient stripeClient = stripeClientFactory.forAccount(accountId);
        Plan plan = planService.retrievePlan(UUID.fromString(accountId.toString()), planId);
        String currency = plan.getCurrency() == null ? "usd" : plan.getCurrency().toLowerCase();

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
                        .setUnitAmountDecimal(toStripeMinorUnitsDecimal(tier.getPricePerUnit(), currency));

                if (tier.getFlatFee() != null && tier.getFlatFee().compareTo(BigDecimal.ZERO) > 0) {
                    tierBuilder.setFlatAmountDecimal(toStripeMinorUnitsDecimal(tier.getFlatFee(), currency));
                }

                if (tier.getUpTo() == null || "inf".equalsIgnoreCase(tier.getUpTo().toString())) {
                    tierBuilder.setUpTo(PriceCreateParams.Tier.UpTo.INF);
                } else {
                    tierBuilder.setUpTo(Long.parseLong(tier.getUpTo().toString()));
                }

                stripeTiers.add(tierBuilder.build());
            }

            PriceCreateParams priceParams = PriceCreateParams.builder()
                    .setCurrency(currency)
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
            PriceCreateParams priceParams = PriceCreateParams.builder()
                    .setCurrency(currency)
                    .setProduct(productId)
                    .setUnitAmountDecimal(toStripeMinorUnitsDecimal(usageModel.getRate(), currency))
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

        // Get Stripe price for the plan (lazily create if the plan predates STRIPE_INTEGRATION setup)
        StripePrice stripePrice = stripePriceRepository
                .findFirstByPlanAndAccountOrderByCreatedAtDesc(subscription.getPlan(), subscription.getAccount())
                .orElse(null);
        if (stripePrice == null) {
            log.info("No StripePrice for plan {}, creating lazily", subscription.getPlan().getId());
            createStripeProductWithPrices(subscription.getPlan().getId(), accountId);
            stripePrice = stripePriceRepository
                    .findFirstByPlanAndAccountOrderByCreatedAtDesc(subscription.getPlan(), subscription.getAccount())
                    .orElseThrow(() -> new IllegalStateException(
                            "Failed to create StripePrice for plan " + subscription.getPlan().getId()));
        }

        SubscriptionCreateParams.Builder subParamsBuilder = SubscriptionCreateParams.builder()
                .setCustomer(stripeCustomer.getStripeCustomerExternalId())
                .addItem(
                        SubscriptionCreateParams.Item.builder()
                                .setPrice(stripePrice.getStripePriceExternalId())
                                .build()
                )
                .putMetadata("tanso_account_id", accountId.toString())
                .putMetadata("tanso_subscription_id", subscriptionId.toString())
                .putMetadata("tanso_customer_id", subscription.getCustomer().getId().toString())
                .setProrationBehavior(SubscriptionCreateParams.ProrationBehavior.NONE);

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

        SubscriptionCreateParams params = subParamsBuilder.build();

        com.stripe.model.Subscription stripeSubscription;
        try {
            stripeSubscription = stripeClient.v1().subscriptions().create(params);
        } catch (InvalidRequestException e) {
            if ("resource_missing".equals(e.getCode()) && e.getMessage() != null && e.getMessage().contains("price")) {
                // Stale StripePrice — delete it, recreate, and retry
                log.warn("Stale StripePrice {} for plan {}, recreating",
                        stripePrice.getStripePriceExternalId(), subscription.getPlan().getId());
                stripePriceRepository.delete(stripePrice);
                createStripeProductWithPrices(subscription.getPlan().getId(), accountId);
                StripePrice freshPrice = stripePriceRepository
                        .findFirstByPlanAndAccountOrderByCreatedAtDesc(subscription.getPlan(), subscription.getAccount())
                        .orElseThrow(() -> new IllegalStateException(
                                "Failed to recreate StripePrice for plan " + subscription.getPlan().getId()));

                // Rebuild params with the fresh price
                SubscriptionCreateParams retryParams = SubscriptionCreateParams.builder()
                        .setCustomer(stripeCustomer.getStripeCustomerExternalId())
                        .addItem(SubscriptionCreateParams.Item.builder()
                                .setPrice(freshPrice.getStripePriceExternalId())
                                .build())
                        .putMetadata("tanso_account_id", accountId.toString())
                        .putMetadata("tanso_subscription_id", subscriptionId.toString())
                        .putMetadata("tanso_customer_id", subscription.getCustomer().getId().toString())
                        .setProrationBehavior(SubscriptionCreateParams.ProrationBehavior.NONE)
                        .build();
                stripeSubscription = stripeClient.v1().subscriptions().create(retryParams);
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

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStripeSubscriptionPrice(UUID subscriptionId, UUID accountId, boolean prorate) throws StripeException {
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

        // Get the latest Stripe Price for the new plan
        StripePrice stripePrice = stripePriceRepository
                .findFirstByPlanAndAccountOrderByCreatedAtDesc(subscription.getPlan(), subscription.getAccount())
                .orElse(null);
        if (stripePrice == null) {
            // Lazily create product+price if the new plan hasn't been synced yet
            log.info("No StripePrice for plan {}, creating lazily", subscription.getPlan().getId());
            createStripeProductWithPrices(subscription.getPlan().getId(), accountId);
            stripePrice = stripePriceRepository
                    .findFirstByPlanAndAccountOrderByCreatedAtDesc(subscription.getPlan(), subscription.getAccount())
                    .orElseThrow(() -> new IllegalStateException(
                            "Failed to create StripePrice for plan " + subscription.getPlan().getId()));
        }

        // Retrieve the current Stripe subscription to get the existing item ID
        com.stripe.model.Subscription currentStripeSub = stripeClient.v1().subscriptions()
                .retrieve(stripeSub.getStripeSubscriptionExternalId());
        String existingItemId = currentStripeSub.getItems().getData().getFirst().getId();

        SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                .addItem(SubscriptionUpdateParams.Item.builder()
                        .setId(existingItemId)
                        .setPrice(stripePrice.getStripePriceExternalId())
                        .build())
                .setProrationBehavior(prorate
                        ? SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS
                        : SubscriptionUpdateParams.ProrationBehavior.NONE)
                .build();

        stripeClient.v1().subscriptions().update(stripeSub.getStripeSubscriptionExternalId(), params);

        log.info("Updated Stripe subscription {} to new price {} for plan {} (prorate={})",
                stripeSub.getStripeSubscriptionExternalId(), stripePrice.getStripePriceExternalId(),
                subscription.getPlan().getId(), prorate);
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
    public void addLineItemToDraftInvoice(String stripeInvoiceId, UUID accountId, BigDecimal amount, String currency, String description) throws StripeException {
        StripeClient stripeClient = stripeClientFactory.forAccount(accountId);

        // Resolve Stripe customer from the invoice
        com.stripe.model.Invoice stripeInvoice = stripeClient.v1().invoices().retrieve(stripeInvoiceId);

        InvoiceItemCreateParams itemParams = InvoiceItemCreateParams.builder()
                .setCustomer(stripeInvoice.getCustomer())
                .setInvoice(stripeInvoiceId)
                .setAmount(toStripeMinorUnits(amount, currency))
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

    // Stripe currencies with no minor unit (amount is already in the smallest unit).
    // https://docs.stripe.com/currencies#zero-decimal
    private static final java.util.Set<String> STRIPE_ZERO_DECIMAL_CURRENCIES = java.util.Set.of(
            "bif", "clp", "djf", "gnf", "jpy", "kmf", "krw", "mga",
            "pyg", "rwf", "ugx", "vnd", "vuv", "xaf", "xof", "xpf");

    private static int stripeMinorUnitExponent(String currency) {
        String code = currency == null ? "usd" : currency.toLowerCase();
        return STRIPE_ZERO_DECIMAL_CURRENCIES.contains(code) ? 0 : 2;
    }

    // Converts a major-unit amount (e.g. dollars) into Stripe's integer smallest unit
    // (e.g. cents), honoring zero-decimal currencies. Rounds to the smallest unit so
    // sub-unit amounts do not throw instead of being charged.
    private static long toStripeMinorUnits(BigDecimal amount, String currency) {
        return amount.movePointRight(stripeMinorUnitExponent(currency))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .longValueExact();
    }

    // Like toStripeMinorUnits but preserves sub-unit precision for per-unit rates
    // (used with Stripe's *_amount_decimal fields, which accept fractional smallest units).
    private static BigDecimal toStripeMinorUnitsDecimal(BigDecimal amount, String currency) {
        return amount.movePointRight(stripeMinorUnitExponent(currency));
    }
}
