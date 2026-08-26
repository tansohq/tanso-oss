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
package com.tansoflow.tansocore.service.internal.spend.implementation;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.VendorConnection;
import com.tansoflow.tansocore.entity.VendorUsageBucket;
import com.tansoflow.tansocore.integration.spend.UsageBucketRecord;
import com.tansoflow.tansocore.integration.spend.VendorUsagePuller;
import com.tansoflow.tansocore.model.exception.VendorApiException;
import com.tansoflow.tansocore.model.spend.VendorProbeResultDto;
import com.tansoflow.tansocore.model.spend.VendorSyncResultDto;
import com.tansoflow.tansocore.model.spend.type.VendorConnectionStatus;
import com.tansoflow.tansocore.model.spend.type.VendorProvider;
import com.tansoflow.tansocore.model.spend.type.VendorUsageSource;
import com.tansoflow.tansocore.repository.VendorConnectionRepository;
import com.tansoflow.tansocore.repository.VendorUsageBucketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendorSyncServiceImplTest {

    @Mock
    private VendorConnectionRepository connectionRepository;
    @Mock
    private VendorUsageBucketRepository bucketRepository;
    @Mock
    private VendorUsagePuller anthropic;
    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Mock
    private com.tansoflow.tansocore.service.internal.spend.SpendBudgetService budgetService;

    private VendorSyncServiceImpl service;
    private final UUID accountId = UUID.randomUUID();
    private final UUID connectionId = UUID.randomUUID();
    private VendorConnection connection;

    @BeforeEach
    void setUp() {
        when(anthropic.provider()).thenReturn(VendorProvider.ANTHROPIC);
        service = new VendorSyncServiceImpl(connectionRepository, bucketRepository, List.of(anthropic), transactionManager, budgetService);
        Account account = new Account();
        account.setId(accountId);
        connection = new VendorConnection();
        connection.setId(connectionId);
        connection.setAccount(account);
        connection.setProvider(VendorProvider.ANTHROPIC);
        connection.setAdminKey("sk-ant-admin01-x");
        lenient().when(connectionRepository.findByIdAndAccountId(connectionId, accountId)).thenReturn(Optional.of(connection));
    }

    @Test
    void syncRewritesTheWindowAndStampsTheConnection() {
        Instant day = Instant.parse("2026-08-20T00:00:00Z");
        when(anthropic.pull(eq("sk-ant-admin01-x"), any(), any())).thenReturn(List.of(
                new UsageBucketRecord(VendorUsageSource.USAGE_API, day, day.plusSeconds(86400), "claude-sonnet-4-5",
                        null, null, null, null, null, 10, 2, 1, 5, null, null, null),
                new UsageBucketRecord(VendorUsageSource.COST_API, day, day.plusSeconds(86400), null,
                        null, null, null, null, "Sonnet input", 0, 0, 0, 0, null, new BigDecimal("12.5"), "USD")));

        VendorSyncResultDto result = service.sync(accountId.toString(), connectionId.toString(),
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 22));

        assertEquals(2, result.getRowsWritten());
        for (VendorUsageSource source : VendorUsageSource.values()) {
            verify(bucketRepository).deleteWindow(connectionId, source,
                    Instant.parse("2026-08-20T00:00:00Z"), Instant.parse("2026-08-22T00:00:00Z"));
        }
        ArgumentCaptor<List<VendorUsageBucket>> saved = ArgumentCaptor.forClass(List.class);
        verify(bucketRepository).saveAll(saved.capture());
        VendorUsageBucket first = saved.getValue().get(0);
        assertEquals(accountId, first.getAccountId());
        assertEquals(connectionId, first.getConnectionId());
        assertEquals(VendorProvider.ANTHROPIC, first.getProvider());
        assertEquals(10, first.getUncachedInputTokens());
        assertEquals(VendorConnectionStatus.ACTIVE, connection.getStatus());
        assertNotNull(connection.getLastSyncedAt());
        assertNull(connection.getLastError());
    }

    @Test
    void longWindowIsChunkedToThirtyOneDays() {
        when(anthropic.pull(anyString(), any(), any())).thenReturn(List.of());
        service.sync(accountId.toString(), connectionId.toString(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 1));
        verify(anthropic).pull("sk-ant-admin01-x", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 2));
        verify(anthropic).pull("sk-ant-admin01-x", LocalDate.of(2026, 7, 2), LocalDate.of(2026, 8, 1));
        verify(anthropic, times(2)).pull(anyString(), any(), any());
    }

    @Test
    void vendorRejectionMarksTheConnectionAndWritesNothing() {
        when(anthropic.pull(anyString(), any(), any())).thenThrow(new VendorApiException(401, "Anthropic admin API returned 401: bad key"));
        assertThrows(VendorApiException.class,
                () -> service.sync(accountId.toString(), connectionId.toString(), null, null));
        assertEquals(VendorConnectionStatus.ERROR, connection.getStatus());
        assertTrue(connection.getLastError().contains("401"));
        verify(bucketRepository, never()).saveAll(any());
        verify(bucketRepository, never()).deleteWindow(any(), any(), any(), any());
    }

    @Test
    void probeReportsWithoutThrowing() {
        doThrow(new VendorApiException(403, "forbidden")).when(anthropic).probe("sk-ant-admin01-x");
        VendorProbeResultDto bad = service.probe(accountId.toString(), connectionId.toString());
        assertFalse(bad.isOk());
        assertEquals(VendorConnectionStatus.ERROR, connection.getStatus());

        org.mockito.Mockito.reset(anthropic);
        VendorProbeResultDto good = service.probe(accountId.toString(), connectionId.toString());
        assertTrue(good.isOk());
        assertEquals(VendorConnectionStatus.ACTIVE, connection.getStatus());
        assertNull(connection.getLastError());
    }

    @Test
    void syncAllKeepsGoingPastOneBadConnection() {
        VendorConnection other = new VendorConnection();
        other.setId(UUID.randomUUID());
        other.setAccount(connection.getAccount());
        other.setProvider(VendorProvider.ANTHROPIC);
        other.setAdminKey("k2");
        when(connectionRepository.findAll()).thenReturn(List.of(connection, other));
        when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));
        when(connectionRepository.findById(other.getId())).thenReturn(Optional.of(other));
        when(anthropic.pull(eq("sk-ant-admin01-x"), any(), any())).thenThrow(new VendorApiException(401, "nope"));
        when(anthropic.pull(eq("k2"), any(), any())).thenReturn(List.of());

        service.syncAll();

        assertEquals(VendorConnectionStatus.ERROR, connection.getStatus());
        assertTrue(connection.getLastError().contains("nope"));
        assertEquals(VendorConnectionStatus.ACTIVE, other.getStatus());
        assertNotNull(other.getLastSyncedAt());
        // one transaction per connection, plus one to record the failure outside the rolled-back one
        verify(transactionManager, times(3)).getTransaction(any());
        verify(transactionManager, times(1)).rollback(any());
    }
}
