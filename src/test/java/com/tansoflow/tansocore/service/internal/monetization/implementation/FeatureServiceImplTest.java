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
package com.tansoflow.tansocore.service.internal.monetization.implementation;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.Feature;
import com.tansoflow.tansocore.mapper.monetization.FeatureMapper;
import com.tansoflow.tansocore.model.feature.FeatureDto;
import com.tansoflow.tansocore.model.feature.request.FeatureRequest;
import com.tansoflow.tansocore.repository.FeatureRepository;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeatureServiceImplTest {

    @Mock
    private FeatureRepository featureRepository;
    @Mock
    private FeatureMapper featureMapper;
    @Mock
    private AccountService accountService;

    @InjectMocks
    private FeatureServiceImpl featureService;

    private String accountId;
    private Account account;
    private FeatureRequest featureRequest;
    private Feature featureEntity;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID().toString();
        account = new Account();
        account.setId(UUID.fromString(accountId));

        featureRequest = new FeatureRequest();
        featureRequest.setKey("test-feature");
        featureRequest.setName("Test Feature");

        featureEntity = new Feature();
        featureEntity.setKey("test-feature");
    }

    @Test
    void testCreateFeature_NullKey_ThrowsException() {
        // Setup
        when(accountService.retrieveAccount(accountId)).thenReturn(account);
        featureEntity.setKey(null);
        when(featureMapper.featureRequestToFeatureEntity(featureRequest)).thenReturn(featureEntity);

        // Execute & Verify
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                featureService.createFeature(accountId, featureRequest));
        assertEquals("Feature key cannot be null", exception.getMessage());
    }

    @Test
    void testCreateFeature_DuplicateKey_ThrowsException() {
        // Setup
        when(accountService.retrieveAccount(accountId)).thenReturn(account);
        when(featureMapper.featureRequestToFeatureEntity(featureRequest)).thenReturn(featureEntity);
        when(featureRepository.existsByKeyAndAccountId("test-feature", account.getId())).thenReturn(true);

        // Execute & Verify
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                featureService.createFeature(accountId, featureRequest));
        assertEquals("Feature with key: test-feature already exists", exception.getMessage());
    }

    @Test
    void testCreateFeature_Success() {
        // Setup
        when(accountService.retrieveAccount(accountId)).thenReturn(account);
        when(featureMapper.featureRequestToFeatureEntity(featureRequest)).thenReturn(featureEntity);
        when(featureRepository.existsByKeyAndAccountId("test-feature", account.getId())).thenReturn(false);
        when(featureRepository.save(any(Feature.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FeatureDto featureDto = new FeatureDto();
        when(featureMapper.featureEntityToFeatureDto(any(Feature.class))).thenReturn(featureDto);

        // Execute
        FeatureDto result = featureService.createFeature(accountId, featureRequest);

        // Verify
        assertEquals(featureDto, result);
        assertEquals(account, featureEntity.getAccount());
        verify(featureRepository).save(featureEntity);
    }

    @Test
    void testUpdateFeature_Success() {
        // Setup
        UUID featureId = UUID.randomUUID();
        when(accountService.retrieveAccount(accountId)).thenReturn(account);
        when(featureRepository.findByIdAndAccount(featureId, account)).thenReturn(Optional.of(featureEntity));
        when(featureRepository.save(featureEntity)).thenReturn(featureEntity);

        FeatureDto featureDto = new FeatureDto();
        when(featureMapper.featureEntityToFeatureDto(featureEntity)).thenReturn(featureDto);

        // Execute
        FeatureDto result = featureService.updateFeature(accountId, featureId, featureRequest);

        // Verify
        assertNotNull(result);
        verify(featureMapper).updateFeatureEntity(featureRequest, featureEntity);
        verify(featureRepository).save(featureEntity);
    }

    @Test
    void testUpdateFeature_DuplicateKey_ThrowsException() {
        // Setup
        UUID featureId = UUID.randomUUID();
        featureRequest.setKey("new-key");
        featureEntity.setKey("old-key");
        
        when(accountService.retrieveAccount(accountId)).thenReturn(account);
        when(featureRepository.findByIdAndAccount(featureId, account)).thenReturn(Optional.of(featureEntity));
        when(featureRepository.existsByKeyAndAccountId("new-key", account.getId())).thenReturn(true);

        // Execute & Verify
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                featureService.updateFeature(accountId, featureId, featureRequest));
        assertEquals("Feature with key: new-key already exists", exception.getMessage());
    }

    @Test
    void testCreateFeature_DuplicateNameDifferentKey_Success() {
        // Setup
        featureRequest.setKey("key2");
        featureRequest.setName("Same Name");
        featureEntity.setKey("key2");
        featureEntity.setName("Same Name");

        when(accountService.retrieveAccount(accountId)).thenReturn(account);
        when(featureMapper.featureRequestToFeatureEntity(featureRequest)).thenReturn(featureEntity);
        when(featureRepository.existsByKeyAndAccountId("key2", account.getId())).thenReturn(false);
        when(featureRepository.save(any(Feature.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FeatureDto featureDto = new FeatureDto();
        when(featureMapper.featureEntityToFeatureDto(any(Feature.class))).thenReturn(featureDto);

        // Execute
        FeatureDto result = featureService.createFeature(accountId, featureRequest);

        // Verify
        assertNotNull(result);
        verify(featureRepository).save(featureEntity);
    }

    @Test
    void testUpdateFeature_NotFound_ThrowsException() {
        // Setup
        UUID featureId = UUID.randomUUID();
        when(accountService.retrieveAccount(accountId)).thenReturn(account);
        when(featureRepository.findByIdAndAccount(featureId, account)).thenReturn(Optional.empty());

        // Execute & Verify
        assertThrows(IllegalArgumentException.class, () ->
                featureService.updateFeature(accountId, featureId, featureRequest));
    }

    @Test
    void testIsOwner_True() {
        // Setup
        String featureId = UUID.randomUUID().toString();
        featureEntity.setAccount(account);

        when(accountService.retrieveAccount(accountId)).thenReturn(account);
        when(featureRepository.findByIdAndAccount(UUID.fromString(featureId), account)).thenReturn(Optional.of(featureEntity));

        // Execute
        boolean result = featureService.isOwner(accountId, featureId);

        // Verify
        assertTrue(result);
    }
}
