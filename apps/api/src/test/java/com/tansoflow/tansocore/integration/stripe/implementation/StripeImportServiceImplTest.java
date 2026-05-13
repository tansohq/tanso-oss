package com.tansoflow.tansocore.integration.stripe.implementation;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Customer;
import com.tansoflow.tansocore.entity.Plan;
import com.tansoflow.tansocore.entity.StripeCustomer;
import com.tansoflow.tansocore.entity.StripeImportJob;
import com.tansoflow.tansocore.entity.StripeProduct;
import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.integration.stripe.StripeClientFactory;
import com.tansoflow.tansocore.model.data.stripe.request.StripeImportStartRequest;
import com.tansoflow.tansocore.model.data.stripe.request.StripeMapProductRequest;
import com.tansoflow.tansocore.model.data.stripe.response.StripeImportStatusResponse;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.PlanRepository;
import com.tansoflow.tansocore.repository.StripeCustomerRepository;
import com.tansoflow.tansocore.repository.StripeImportJobRepository;
import com.tansoflow.tansocore.repository.StripeProductPlansRepository;
import com.tansoflow.tansocore.repository.StripeSubscriptionRepository;
import com.tansoflow.tansocore.repository.SubscriptionRepository;
import com.tansoflow.tansocore.service.internal.account.CustomerService;
import com.tansoflow.tansocore.service.internal.monetization.EntitlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeImportServiceImplTest {

    @InjectMocks
    private StripeImportServiceImpl stripeImportService;

    @Mock
    private StripeClientFactory stripeClientFactory;
    @Mock
    private StripeCustomerRepository stripeCustomerRepository;
    @Mock
    private StripeProductPlansRepository stripeProductPlansRepository;
    @Mock
    private StripeSubscriptionRepository stripeSubscriptionRepository;
    @Mock
    private StripeImportJobRepository stripeImportJobRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private PlanRepository planRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private EntitlementService entitlementService;

    private Account account;
    private UUID accountId;
    private Plan plan;

    @BeforeEach
    void setUp() {
        account = new Account();
        account.setId(UUID.randomUUID());
        accountId = account.getId();

        plan = new Plan();
        plan.setId(UUID.randomUUID());
    }

    // ── mapProduct tests ──────────────────────────────────────────────────

    @Test
    void mapProduct_CreatesStripeProductBridgeEntry() {
        StripeMapProductRequest request = new StripeMapProductRequest();
        request.setStripeProductId("prod_123");
        request.setTansoPlanId(plan.getId());

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(planRepository.findByIdAndAccount(plan.getId(), account)).thenReturn(Optional.of(plan));
        when(stripeProductPlansRepository.existsByStripeProductExternalIdAndAccount("prod_123", account))
                .thenReturn(false);

        stripeImportService.mapProduct(accountId, request);

        ArgumentCaptor<StripeProduct> captor = ArgumentCaptor.forClass(StripeProduct.class);
        verify(stripeProductPlansRepository).save(captor.capture());

        StripeProduct saved = captor.getValue();
        assertEquals("prod_123", saved.getStripeProductExternalId());
        assertEquals(plan, saved.getPlan());
        assertEquals(account, saved.getAccount());
    }

    @Test
    void mapProduct_AlreadyMapped_ThrowsException() {
        StripeMapProductRequest request = new StripeMapProductRequest();
        request.setStripeProductId("prod_123");
        request.setTansoPlanId(plan.getId());

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(planRepository.findByIdAndAccount(plan.getId(), account)).thenReturn(Optional.of(plan));
        when(stripeProductPlansRepository.existsByStripeProductExternalIdAndAccount("prod_123", account))
                .thenReturn(true);

        assertThrows(IllegalStateException.class, () ->
                stripeImportService.mapProduct(accountId, request));

        verify(stripeProductPlansRepository, never()).save(any());
    }

    @Test
    void mapProduct_PlanNotFound_ThrowsException() {
        StripeMapProductRequest request = new StripeMapProductRequest();
        request.setStripeProductId("prod_123");
        request.setTansoPlanId(UUID.randomUUID());

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(planRepository.findByIdAndAccount(request.getTansoPlanId(), account)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                stripeImportService.mapProduct(accountId, request));
    }

    // ── getImportStatus tests ─────────────────────────────────────────────

    @Test
    void getImportStatus_ReturnsStatusForCorrectAccount() {
        UUID jobId = UUID.randomUUID();
        StripeImportJob job = new StripeImportJob();
        job.setId(jobId);
        job.setAccount(account);
        job.setStatus("COMPLETED");
        job.setTotalItems(5);
        job.setProcessedItems(5);
        job.setFailedItems(0);

        when(stripeImportJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        StripeImportStatusResponse response = stripeImportService.getImportStatus(accountId, jobId);

        assertEquals(jobId, response.getJobId());
        assertEquals("COMPLETED", response.getStatus());
        assertEquals(5, response.getTotalItems());
        assertEquals(5, response.getProcessedItems());
    }

    @Test
    void getImportStatus_WrongAccount_ThrowsException() {
        UUID jobId = UUID.randomUUID();
        Account otherAccount = new Account();
        otherAccount.setId(UUID.randomUUID());

        StripeImportJob job = new StripeImportJob();
        job.setId(jobId);
        job.setAccount(otherAccount);

        when(stripeImportJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        assertThrows(IllegalArgumentException.class, () ->
                stripeImportService.getImportStatus(accountId, jobId));
    }

    @Test
    void getImportStatus_JobNotFound_ThrowsException() {
        UUID jobId = UUID.randomUUID();
        when(stripeImportJobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                stripeImportService.getImportStatus(accountId, jobId));
    }

    // ── startImport tests ─────────────────────────────────────────────────

    @Test
    void startImport_CreatesJobAndProcessesMappings() {
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());

        StripeImportStartRequest request = new StripeImportStartRequest();
        StripeImportStartRequest.ProductMapping pm = new StripeImportStartRequest.ProductMapping();
        pm.setStripeProductId("prod_abc");
        pm.setTansoPlanId(plan.getId());
        request.setProductMappings(List.of(pm));

        StripeImportStartRequest.CustomerMapping cm = new StripeImportStartRequest.CustomerMapping();
        cm.setStripeCustomerId("cus_abc");
        cm.setTansoCustomerId(customer.getId());
        cm.setAutoCreate(false);
        request.setCustomerMappings(List.of(cm));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(planRepository.findByIdAndAccount(plan.getId(), account)).thenReturn(Optional.of(plan));
        when(stripeProductPlansRepository.existsByStripeProductExternalIdAndAccount("prod_abc", account))
                .thenReturn(false);
        when(stripeCustomerRepository.existsByStripeCustomerExternalIdAndAccount("cus_abc", account))
                .thenReturn(false);
        when(customerService.retrieveCustomer(customer.getId())).thenReturn(customer);
        // Mock stripeClientFactory to avoid NPE during subscription import
        when(stripeClientFactory.forAccount(accountId)).thenThrow(new RuntimeException("No Stripe key configured"));

        StripeImportStatusResponse response = stripeImportService.startImport(accountId, request);

        // Job should fail due to missing Stripe key, but product & customer mappings should have been processed
        assertEquals("FAILED", response.getStatus());

        // Verify product bridge entry was created
        verify(stripeProductPlansRepository).save(any(StripeProduct.class));
        // Verify customer bridge entry was created
        verify(stripeCustomerRepository).save(any(StripeCustomer.class));
    }

    @Test
    void startImport_SkipsAlreadyMappedProducts() {
        StripeImportStartRequest request = new StripeImportStartRequest();
        StripeImportStartRequest.ProductMapping pm = new StripeImportStartRequest.ProductMapping();
        pm.setStripeProductId("prod_existing");
        pm.setTansoPlanId(plan.getId());
        request.setProductMappings(List.of(pm));
        request.setCustomerMappings(List.of());

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(planRepository.findByIdAndAccount(plan.getId(), account)).thenReturn(Optional.of(plan));
        when(stripeProductPlansRepository.existsByStripeProductExternalIdAndAccount("prod_existing", account))
                .thenReturn(true);
        when(stripeClientFactory.forAccount(accountId)).thenThrow(new RuntimeException("No Stripe key"));

        stripeImportService.startImport(accountId, request);

        // Should not create another bridge entry
        verify(stripeProductPlansRepository, never()).save(any(StripeProduct.class));
    }
}
