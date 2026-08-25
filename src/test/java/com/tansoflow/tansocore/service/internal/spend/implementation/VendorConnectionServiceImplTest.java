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
import com.tansoflow.tansocore.model.spend.type.VendorProvider;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.VendorConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendorConnectionServiceImplTest {

    @Mock
    private VendorConnectionRepository vendorConnectionRepository;
    @Mock
    private AccountRepository accountRepository;

    private VendorConnectionServiceImpl service;
    private final UUID accountId = UUID.randomUUID();
    private Account account;

    @BeforeEach
    void setUp() {
        service = new VendorConnectionServiceImpl(vendorConnectionRepository, accountRepository);
        account = new Account();
        account.setId(accountId);
    }

    @Test
    void createStoresTrimmedKeyAndReturnsOnlyAHint() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(vendorConnectionRepository.save(any())).thenAnswer(inv -> {
            VendorConnection c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        CreateVendorConnectionRequest request = new CreateVendorConnectionRequest();
        request.setProvider(VendorProvider.ANTHROPIC);
        request.setLabel("  Engineering org ");
        request.setAdminKey(" sk-ant-admin01-abcdef-wxyz \n");

        VendorConnectionDto dto = service.create(accountId.toString(), request);

        ArgumentCaptor<VendorConnection> saved = ArgumentCaptor.forClass(VendorConnection.class);
        verify(vendorConnectionRepository).save(saved.capture());
        assertEquals("sk-ant-admin01-abcdef-wxyz", saved.getValue().getAdminKey());
        assertEquals(account, saved.getValue().getAccount());
        assertEquals("Engineering org", dto.getLabel());
        assertEquals("wxyz", dto.getKeyHint());
        assertEquals(VendorProvider.ANTHROPIC, dto.getProvider());
        assertNotNull(dto.getId());
    }

    @Test
    void shortKeyHintIsTheWholeKey() {
        assertEquals("ab", VendorConnectionServiceImpl.hintOf("ab"));
        assertEquals("cdef", VendorConnectionServiceImpl.hintOf("abcdef"));
    }

    @Test
    void createForUnknownAccountFails() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());
        CreateVendorConnectionRequest request = new CreateVendorConnectionRequest();
        request.setProvider(VendorProvider.OPENAI);
        request.setLabel("x");
        request.setAdminKey("sk-admin-1234");
        assertThrows(ResourceNotFoundException.class, () -> service.create(accountId.toString(), request));
        verify(vendorConnectionRepository, never()).save(any());
    }

    @Test
    void deleteIsSoftAndScopedToTheAccount() {
        UUID id = UUID.randomUUID();
        VendorConnection connection = new VendorConnection();
        connection.setId(id);
        connection.setAdminKey("sk-ant-admin01-secret");
        when(vendorConnectionRepository.findByIdAndAccountId(id, accountId)).thenReturn(Optional.of(connection));

        service.delete(accountId.toString(), id.toString());

        assertNotNull(connection.getDeletedAt());
        assertEquals("", connection.getAdminKey());
        verify(vendorConnectionRepository).save(connection);
    }

    @Test
    void deleteOfAnotherAccountsConnectionIsNotFound() {
        UUID id = UUID.randomUUID();
        when(vendorConnectionRepository.findByIdAndAccountId(id, accountId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.delete(accountId.toString(), id.toString()));
    }
}
