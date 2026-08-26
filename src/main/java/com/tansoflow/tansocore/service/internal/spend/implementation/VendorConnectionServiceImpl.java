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

import com.tansoflow.tansocore.util.OutboundUrlPolicy;
import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.VendorConnection;
import com.tansoflow.tansocore.integration.spend.VendorUsagePuller;
import com.tansoflow.tansocore.model.exception.VendorApiException;
import com.tansoflow.tansocore.model.spend.type.VendorProvider;
import com.tansoflow.tansocore.integration.spend.VendorUsagePuller;
import com.tansoflow.tansocore.model.exception.VendorApiException;
import com.tansoflow.tansocore.model.spend.type.VendorProvider;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.model.spend.VendorConnectionDto;
import com.tansoflow.tansocore.model.spend.request.CreateVendorConnectionRequest;
import com.tansoflow.tansocore.model.spend.type.VendorConnectionStatus;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.VendorConnectionRepository;
import com.tansoflow.tansocore.repository.VendorActorMetricRepository;
import com.tansoflow.tansocore.repository.VendorUsageBucketRepository;
import com.tansoflow.tansocore.service.internal.spend.VendorConnectionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.modules.build.enabled", havingValue = "true", matchIfMissing = true)
public class VendorConnectionServiceImpl implements VendorConnectionService {
    private final VendorConnectionRepository vendorConnectionRepository;
    private final OutboundUrlPolicy outboundUrlPolicy;
    private final VendorUsageBucketRepository vendorUsageBucketRepository;
    private final VendorActorMetricRepository vendorActorMetricRepository;
    private final AccountRepository accountRepository;
    private final java.util.Map<VendorProvider, VendorUsagePuller> pullers = new java.util.EnumMap<>(VendorProvider.class);

    public VendorConnectionServiceImpl(VendorConnectionRepository vendorConnectionRepository,
                                       VendorUsageBucketRepository vendorUsageBucketRepository,
                                       VendorActorMetricRepository vendorActorMetricRepository,
                                       AccountRepository accountRepository,
                                       List<VendorUsagePuller> pullers,
                                      OutboundUrlPolicy outboundUrlPolicy) {
        this.outboundUrlPolicy = outboundUrlPolicy;
        this.vendorConnectionRepository = vendorConnectionRepository;
        this.vendorUsageBucketRepository = vendorUsageBucketRepository;
        this.vendorActorMetricRepository = vendorActorMetricRepository;
        this.accountRepository = accountRepository;
        for (VendorUsagePuller p : pullers) {
            this.pullers.put(p.provider(), p);
        }
    }

    @Override
    public List<VendorConnectionDto> list(String accountId) {
        return vendorConnectionRepository.findAllByAccountIdOrderByCreatedAtAsc(UUID.fromString(accountId))
                .stream().map(VendorConnectionServiceImpl::toDto).toList();
    }

    @Override
    @Transactional
    public VendorConnectionDto create(String accountId, CreateVendorConnectionRequest request) {
        Account account = accountRepository.findById(UUID.fromString(accountId))
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
        String adminKey = request.getAdminKey().trim();
        VendorUsagePuller forProvider = pullers.get(request.getProvider());
        String scope = request.getScope() == null || request.getScope().isBlank() ? null : request.getScope().trim();
        if (forProvider != null && forProvider.requiresScope() && scope == null) {
            throw new IllegalArgumentException(request.getProvider() + " needs a scope (the GitHub organization)");
        }
        if (forProvider != null && !forProvider.requiresScope()) {
            scope = null;   // means nothing for this provider; storing it would only mislead the list
        }
        if (request.getProvider() == VendorProvider.LITELLM) {
            scope = outboundUrlPolicy.check(scope, "The LiteLLM proxy URL");
        }
        String label = request.getLabel().trim();
        for (VendorConnection existing : vendorConnectionRepository.findAllByAccountIdOrderByCreatedAtAsc(account.getId())) {
            if (existing.getProvider() == request.getProvider() && existing.getLabel().equalsIgnoreCase(label)) {
                throw new IllegalArgumentException("A " + request.getProvider() + " connection labelled \"" + existing.getLabel() + "\" already exists — replace its key instead");
            }
        }
        VendorConnection connection = new VendorConnection();
        connection.setAccount(account);
        connection.setProvider(request.getProvider());
        connection.setLabel(label);
        connection.setAdminKey(adminKey);
        connection.setKeyHint(hintOf(adminKey));
        connection.setScope(scope);
        connection = vendorConnectionRepository.saveAndFlush(connection);
        // Check the key now so the row never shows "OK" for a credential nobody has tried.
        VendorUsagePuller puller = pullers.get(connection.getProvider());
        if (puller != null) {
            try {
                puller.probe(adminKey, connection.getScope());
                connection.setStatus(VendorConnectionStatus.ACTIVE);
                connection.setLastError(null);
            } catch (VendorApiException e) {
                connection.setStatus(VendorConnectionStatus.ERROR);
                connection.setLastError(e.getMessage());
            }
            connection = vendorConnectionRepository.save(connection);
        }
        return toDto(connection);
    }

    @Override
    @Transactional
    public VendorConnectionDto replaceKey(String accountId, String connectionId, String adminKey) {
        VendorConnection connection = vendorConnectionRepository
                .findByIdAndAccountId(UUID.fromString(connectionId), UUID.fromString(accountId))
                .orElseThrow(() -> new ResourceNotFoundException("Vendor connection not found: " + connectionId));
        String trimmed = adminKey.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("adminKey is empty");
        }
        connection.setAdminKey(trimmed);
        connection.setKeyHint(hintOf(trimmed));
        connection.setStatus(VendorConnectionStatus.ACTIVE);
        connection.setLastError(null);
        return toDto(vendorConnectionRepository.save(connection));
    }

    @Override
    @Transactional
    public void delete(String accountId, String connectionId) {
        VendorConnection connection = vendorConnectionRepository
                .findByIdAndAccountId(UUID.fromString(connectionId), UUID.fromString(accountId))
                .orElseThrow(() -> new ResourceNotFoundException("Vendor connection not found: " + connectionId));
        // Soft-deleted for the audit trail, but neither the credential nor the
        // pulled usage outlives the disconnect: reconnecting the same org would
        // otherwise count its window twice.
        vendorUsageBucketRepository.deleteByConnectionId(connection.getId());
        vendorActorMetricRepository.deleteByConnectionId(connection.getId());
        connection.setAdminKey("");
        connection.setDeletedAt(Instant.now());
        vendorConnectionRepository.save(connection);
    }

    static String hintOf(String adminKey) {
        return adminKey.length() <= 4 ? "" : adminKey.substring(adminKey.length() - 4);
    }

    private static VendorConnectionDto toDto(VendorConnection connection) {
        return VendorConnectionDto.builder()
                .id(connection.getId().toString())
                .provider(connection.getProvider())
                .label(connection.getLabel())
                .keyHint(connection.getKeyHint())
                .scope(connection.getScope())
                .status(connection.getStatus())
                .lastError(connection.getLastError())
                .lastSyncedAt(connection.getLastSyncedAt())
                .createdAt(connection.getCreatedAt())
                .build();
    }
}
