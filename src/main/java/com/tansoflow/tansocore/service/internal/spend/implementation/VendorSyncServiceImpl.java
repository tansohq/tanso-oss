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

import jakarta.persistence.PersistenceContext;
import jakarta.persistence.EntityManager;
import com.tansoflow.tansocore.entity.VendorConnection;
import com.tansoflow.tansocore.entity.VendorActorMetric;
import com.tansoflow.tansocore.entity.VendorUsageBucket;
import com.tansoflow.tansocore.integration.spend.ActorMetricRecord;
import com.tansoflow.tansocore.integration.spend.UsageBucketRecord;
import com.tansoflow.tansocore.integration.spend.VendorUsagePuller;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.model.exception.VendorApiException;
import com.tansoflow.tansocore.model.spend.VendorProbeResultDto;
import com.tansoflow.tansocore.model.spend.VendorSyncResultDto;
import com.tansoflow.tansocore.model.spend.type.VendorConnectionStatus;
import com.tansoflow.tansocore.model.spend.type.VendorProvider;
import com.tansoflow.tansocore.model.spend.type.VendorUsageSource;
import com.tansoflow.tansocore.repository.VendorConnectionRepository;
import com.tansoflow.tansocore.repository.VendorActorMetricRepository;
import com.tansoflow.tansocore.repository.VendorUsageBucketRepository;
import com.tansoflow.tansocore.service.internal.spend.SpendBudgetService;
import com.tansoflow.tansocore.service.internal.spend.VendorSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.modules.build.enabled", havingValue = "true", matchIfMissing = true)
public class VendorSyncServiceImpl implements VendorSyncService {
    static final int DEFAULT_WINDOW_DAYS = 30;
    static final int JOB_WINDOW_DAYS = 3;

    private final VendorConnectionRepository connectionRepository;
    private final VendorUsageBucketRepository bucketRepository;
    private final VendorActorMetricRepository actorMetricRepository;
    private final Map<VendorProvider, VendorUsagePuller> pullers = new EnumMap<>(VendorProvider.class);
    private final TransactionTemplate transactionTemplate;
    private final SpendBudgetService budgetService;
    @PersistenceContext
    private EntityManager entityManager;

    public VendorSyncServiceImpl(VendorConnectionRepository connectionRepository,
                                 VendorUsageBucketRepository bucketRepository,
                                 VendorActorMetricRepository actorMetricRepository,
                                 List<VendorUsagePuller> pullers,
                                 PlatformTransactionManager transactionManager,
                                 SpendBudgetService budgetService) {
        this.connectionRepository = connectionRepository;
        this.bucketRepository = bucketRepository;
        this.actorMetricRepository = actorMetricRepository;
        this.budgetService = budgetService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        for (VendorUsagePuller puller : pullers) {
            this.pullers.put(puller.provider(), puller);
        }
    }

    @Override
    @Transactional
    public VendorProbeResultDto probe(String accountId, String connectionId) {
        VendorConnection connection = require(accountId, connectionId);
        try {
            puller(connection).probe(connection.getAdminKey(), connection.getScope());
            markHealthy(connection);
            return VendorProbeResultDto.builder().ok(true).message("The vendor accepted the key.").build();
        } catch (VendorApiException e) {
            markFailed(connection, e);
            return VendorProbeResultDto.builder().ok(false).message(e.getMessage()).build();
        }
    }

    @Override
    public VendorSyncResultDto sync(String accountId, String connectionId, LocalDate from, LocalDate to) {
        VendorConnection connection = require(accountId, connectionId);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate end = to != null ? to : today.plusDays(1);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_WINDOW_DAYS);
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("from must be before to");
        }
        int rows = syncWindow(connection.getId(), start, end);
        evaluateQuietly(accountId);
        return VendorSyncResultDto.builder()
                .connectionId(connection.getId().toString()).from(start).to(end).rowsWritten(rows).build();
    }

    @Override
    public void syncAll() {
        LocalDate end = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        LocalDate from = end.minusDays(JOB_WINDOW_DAYS);
        for (VendorConnection listed : connectionRepository.findAll()) {
            try {
                syncWindow(listed.getId(), from, end);
            } catch (RuntimeException e) {
                // Already marked on the connection; one bad vendor must not stop the others.
                log.warn("Vendor sync failed for connection {}: {}", listed.getId(), e.getMessage());
                continue;
            }
            evaluateQuietly(listed.getAccount().getId().toString());
        }
    }

    /**
     * The vendor calls run outside any transaction (a 30-day Copilot pull is
     * dozens of HTTP round trips); the delete+rewrite is one short transaction
     * under a per-connection advisory lock, so a manual sync and the hourly job
     * cannot interleave and double the window. Any failure is recorded on the
     * connection in its own transaction and rethrown.
     */
    private int syncWindow(UUID connectionId, LocalDate from, LocalDate to) {
        VendorConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor connection not found: " + connectionId));
        List<UsageBucketRecord> records = new ArrayList<>();
        List<ActorMetricRecord> actorRecords = new ArrayList<>();
        try {
            VendorUsagePuller puller = puller(connection);
            int window = puller.maxWindowDays();
            for (LocalDate chunk = from; chunk.isBefore(to); chunk = chunk.plusDays(window)) {
                LocalDate chunkEnd = chunk.plusDays(window).isBefore(to) ? chunk.plusDays(window) : to;
                VendorUsagePuller.PullResult r = puller.pullAll(connection.getAdminKey(), connection.getScope(), chunk, chunkEnd);
                records.addAll(r.usage());
                actorRecords.addAll(r.actors());
            }
        } catch (RuntimeException e) {
            transactionTemplate.executeWithoutResult(status ->
                    connectionRepository.findById(connectionId).ifPresent(c -> markFailed(c, e)));
            throw e;
        }
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.atStartOfDay(ZoneOffset.UTC).toInstant();
        // A vendor that answers with a different shape reads as "no rows"; do not let that erase a window we had.
        List<UsageBucketRecord> inWindow = records.stream()
                .filter(r -> !r.bucketStart().isBefore(fromInstant) && r.bucketStart().isBefore(toInstant)).toList();
        if (inWindow.size() < records.size()) {
            log.warn("Vendor sync {}: dropped {} rows dated outside [{}, {})", connectionId, records.size() - inWindow.size(), from, to);
        }
        Integer written;
        try {
            written = transactionTemplate.execute(status -> {
                lock(connectionId);
                VendorConnection c = connectionRepository.findById(connectionId)
                        .orElseThrow(() -> new ResourceNotFoundException("Vendor connection not found: " + connectionId));
                if (inWindow.isEmpty()) {
                    long had = bucketRepository.countByConnectionIdAndBucketStartGreaterThanEqualAndBucketStartLessThan(connectionId, fromInstant, toInstant);
                    if (had > 0) {
                        throw new VendorApiException(502, "The vendor returned no usage for [" + from + ", " + to + ") where " + had
                                + " rows were pulled before — the report shape may have changed; the window was left as it was");
                    }
                }
                for (VendorUsageSource source : VendorUsageSource.values()) {
                    bucketRepository.deleteWindow(connectionId, source, fromInstant, toInstant);
                }
                List<VendorUsageBucket> rows = inWindow.stream().map(r -> toEntity(c, r)).toList();
                bucketRepository.saveAll(rows);
                actorMetricRepository.deleteWindow(connectionId, from, to);
                actorMetricRepository.saveAll(actorRecords.stream().map(r -> toEntity(c, r)).toList());
                markHealthy(c);
                c.setLastSyncedAt(Instant.now());
                connectionRepository.save(c);
                log.info("Vendor sync {} [{}, {}): {} rows", connectionId, from, to, rows.size());
                return rows.size();
            });
        } catch (RuntimeException e) {
            transactionTemplate.executeWithoutResult(status ->
                    connectionRepository.findById(connectionId).ifPresent(c -> markFailed(c, e)));
            throw e;
        }
        return written == null ? 0 : written;
    }

    private void lock(UUID connectionId) {
        if (entityManager != null) {   // null only in unit tests that construct the service by hand
            entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:id))")
                    .setParameter("id", connectionId.toString()).getSingleResult();
        }
    }

    private void evaluateQuietly(String accountId) {
        try {
            budgetService.evaluate(accountId);
        } catch (RuntimeException e) {
            log.warn("Budget evaluation after sync failed for account {}: {}", accountId, e.getMessage(), e);
        }
    }

    private VendorUsagePuller puller(VendorConnection connection) {
        VendorUsagePuller puller = pullers.get(connection.getProvider());
        if (puller == null) {
            throw new IllegalStateException("No usage puller for " + connection.getProvider());
        }
        return puller;
    }

    private void markHealthy(VendorConnection connection) {
        connection.setStatus(VendorConnectionStatus.ACTIVE);
        connection.setLastError(null);
        connectionRepository.save(connection);
    }

    private void markFailed(VendorConnection connection, RuntimeException e) {
        connection.setStatus(VendorConnectionStatus.ERROR);
        connection.setLastError(e instanceof VendorApiException ? e.getMessage() : e.getClass().getSimpleName() + ": " + e.getMessage());
        connectionRepository.save(connection);
    }

    private VendorConnection require(String accountId, String connectionId) {
        return connectionRepository.findByIdAndAccountId(UUID.fromString(connectionId), UUID.fromString(accountId))
                .orElseThrow(() -> new ResourceNotFoundException("Vendor connection not found: " + connectionId));
    }

    static VendorActorMetric toEntity(VendorConnection connection, ActorMetricRecord r) {
        VendorActorMetric m = new VendorActorMetric();
        m.setAccountId(connection.getAccount().getId());
        m.setConnectionId(connection.getId());
        m.setProvider(connection.getProvider());
        m.setDay(r.day());
        m.setActorId(r.actorId());
        m.setTool(r.tool());
        m.setSessions(r.sessions());
        m.setRequests(r.requests());
        m.setLinesAdded(r.linesAdded());
        m.setLinesRemoved(r.linesRemoved());
        m.setLinesSuggested(r.linesSuggested());
        m.setAccepted(r.accepted());
        m.setRejected(r.rejected());
        m.setCommits(r.commits());
        m.setPullRequests(r.pullRequests());
        m.setCreditsUsed(r.creditsUsed());
        m.setEstimatedCostCents(r.estimatedCostCents());
        return m;
    }

    static VendorUsageBucket toEntity(VendorConnection connection, UsageBucketRecord r) {
        VendorUsageBucket b = new VendorUsageBucket();
        b.setAccountId(connection.getAccount().getId());
        b.setConnectionId(connection.getId());
        b.setProvider(connection.getProvider());
        b.setSource(r.source());
        b.setBucketStart(r.bucketStart());
        b.setBucketEnd(r.bucketEnd());
        b.setModel(r.model());
        b.setWorkspaceId(r.workspaceId());
        b.setVendorApiKeyId(r.vendorApiKeyId());
        b.setActorId(r.actorId());
        b.setServiceTier(r.serviceTier());
        b.setDescription(r.description());
        b.setUncachedInputTokens(r.uncachedInputTokens());
        b.setCacheReadTokens(r.cacheReadTokens());
        b.setCacheCreationTokens(r.cacheCreationTokens());
        b.setOutputTokens(r.outputTokens());
        b.setRequests(r.requests());
        b.setVendorCostCents(r.vendorCostCents());
        b.setCurrency(r.currency());
        return b;
    }
}
