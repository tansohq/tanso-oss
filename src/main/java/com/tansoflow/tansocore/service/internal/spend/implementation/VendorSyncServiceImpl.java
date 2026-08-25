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

import com.tansoflow.tansocore.entity.VendorConnection;
import com.tansoflow.tansocore.entity.VendorUsageBucket;
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
import com.tansoflow.tansocore.repository.VendorUsageBucketRepository;
import com.tansoflow.tansocore.service.internal.spend.VendorSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    /** Vendors allow at most 31 daily buckets per page; keeping windows under that keeps a sync to one page per source. */
    static final int MAX_WINDOW_DAYS = 31;
    static final int DEFAULT_WINDOW_DAYS = 30;
    static final int JOB_WINDOW_DAYS = 3;

    private final VendorConnectionRepository connectionRepository;
    private final VendorUsageBucketRepository bucketRepository;
    private final Map<VendorProvider, VendorUsagePuller> pullers = new EnumMap<>(VendorProvider.class);

    public VendorSyncServiceImpl(VendorConnectionRepository connectionRepository,
                                 VendorUsageBucketRepository bucketRepository,
                                 List<VendorUsagePuller> pullers) {
        this.connectionRepository = connectionRepository;
        this.bucketRepository = bucketRepository;
        for (VendorUsagePuller puller : pullers) {
            this.pullers.put(puller.provider(), puller);
        }
    }

    @Override
    @Transactional
    public VendorProbeResultDto probe(String accountId, String connectionId) {
        VendorConnection connection = require(accountId, connectionId);
        try {
            puller(connection).probe(connection.getAdminKey());
            markHealthy(connection);
            return VendorProbeResultDto.builder().ok(true).message("The vendor accepted the key.").build();
        } catch (VendorApiException e) {
            markFailed(connection, e);
            return VendorProbeResultDto.builder().ok(false).message(e.getMessage()).build();
        }
    }

    @Override
    @Transactional
    public VendorSyncResultDto sync(String accountId, String connectionId, LocalDate from, LocalDate to) {
        VendorConnection connection = require(accountId, connectionId);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate end = to != null ? to : today.plusDays(1);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_WINDOW_DAYS);
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("from must be before to");
        }
        int rows = pullWindow(connection, start, end);
        return VendorSyncResultDto.builder()
                .connectionId(connection.getId().toString()).from(start).to(end).rowsWritten(rows).build();
    }

    @Override
    public void syncAll() {
        LocalDate end = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        for (VendorConnection connection : connectionRepository.findAll()) {
            try {
                syncOne(connection.getId(), end.minusDays(JOB_WINDOW_DAYS), end);
            } catch (VendorApiException e) {
                // Already recorded on the connection; keep going so one bad key does not stall the rest.
                log.warn("Vendor sync failed for connection {}: {}", connection.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    protected void syncOne(UUID connectionId, LocalDate from, LocalDate to) {
        VendorConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor connection not found: " + connectionId));
        pullWindow(connection, from, to);
    }

    private int pullWindow(VendorConnection connection, LocalDate from, LocalDate to) {
        VendorUsagePuller puller = puller(connection);
        List<UsageBucketRecord> records = new ArrayList<>();
        try {
            for (LocalDate chunk = from; chunk.isBefore(to); chunk = chunk.plusDays(MAX_WINDOW_DAYS)) {
                LocalDate chunkEnd = chunk.plusDays(MAX_WINDOW_DAYS).isBefore(to) ? chunk.plusDays(MAX_WINDOW_DAYS) : to;
                records.addAll(puller.pull(connection.getAdminKey(), chunk, chunkEnd));
            }
        } catch (VendorApiException e) {
            markFailed(connection, e);
            throw e;
        }
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to.atStartOfDay(ZoneOffset.UTC).toInstant();
        for (VendorUsageSource source : VendorUsageSource.values()) {
            bucketRepository.deleteWindow(connection.getId(), source, fromInstant, toInstant);
        }
        List<VendorUsageBucket> rows = records.stream().map(r -> toEntity(connection, r)).toList();
        bucketRepository.saveAll(rows);
        markHealthy(connection);
        connection.setLastSyncedAt(Instant.now());
        connectionRepository.save(connection);
        log.info("Vendor sync {} [{}, {}): {} rows", connection.getId(), from, to, rows.size());
        return rows.size();
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

    private void markFailed(VendorConnection connection, VendorApiException e) {
        connection.setStatus(VendorConnectionStatus.ERROR);
        connection.setLastError(e.getMessage());
        connectionRepository.save(connection);
    }

    private VendorConnection require(String accountId, String connectionId) {
        return connectionRepository.findByIdAndAccountId(UUID.fromString(connectionId), UUID.fromString(accountId))
                .orElseThrow(() -> new ResourceNotFoundException("Vendor connection not found: " + connectionId));
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
