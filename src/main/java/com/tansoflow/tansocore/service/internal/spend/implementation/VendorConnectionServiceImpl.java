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
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.model.spend.VendorConnectionDto;
import com.tansoflow.tansocore.model.spend.request.CreateVendorConnectionRequest;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.VendorConnectionRepository;
import com.tansoflow.tansocore.service.internal.spend.VendorConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.modules.build.enabled", havingValue = "true", matchIfMissing = true)
public class VendorConnectionServiceImpl implements VendorConnectionService {
    private final VendorConnectionRepository vendorConnectionRepository;
    private final AccountRepository accountRepository;

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
        VendorConnection connection = new VendorConnection();
        connection.setAccount(account);
        connection.setProvider(request.getProvider());
        connection.setLabel(request.getLabel().trim());
        connection.setAdminKey(adminKey);
        connection.setKeyHint(hintOf(adminKey));
        return toDto(vendorConnectionRepository.save(connection));
    }

    @Override
    @Transactional
    public void delete(String accountId, String connectionId) {
        VendorConnection connection = vendorConnectionRepository
                .findByIdAndAccountId(UUID.fromString(connectionId), UUID.fromString(accountId))
                .orElseThrow(() -> new ResourceNotFoundException("Vendor connection not found: " + connectionId));
        // Soft-deleted for the audit trail, but the credential itself does not
        // need to outlive the disconnect.
        connection.setAdminKey("");
        connection.setDeletedAt(Instant.now());
        vendorConnectionRepository.save(connection);
    }

    static String hintOf(String adminKey) {
        return adminKey.length() <= 4 ? adminKey : adminKey.substring(adminKey.length() - 4);
    }

    private static VendorConnectionDto toDto(VendorConnection connection) {
        return VendorConnectionDto.builder()
                .id(connection.getId().toString())
                .provider(connection.getProvider())
                .label(connection.getLabel())
                .keyHint(connection.getKeyHint())
                .status(connection.getStatus())
                .lastError(connection.getLastError())
                .lastSyncedAt(connection.getLastSyncedAt())
                .createdAt(connection.getCreatedAt())
                .build();
    }
}
