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
package com.tansoflow.tansocore.controller.tanso.monetization;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.monetization.PlanFeatureRuleDto;
import com.tansoflow.tansocore.model.monetization.request.PlanFeatureLinkedDiffRequest;
import com.tansoflow.tansocore.model.monetization.request.PlanFeatureRuleRequest;
import com.tansoflow.tansocore.model.monetization.response.PlanFeatureLinkedDiffResponse;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.monetization.PlanFeatureRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonetizationRulesControllerTest {

    @Mock
    private PlanFeatureRuleService planFeatureRuleService;

    @InjectMocks
    private MonetizationRulesController monetizationRulesController;

    private UserContext userContext;
    private final String accountId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userContext = new UserContext(accountId, "test-api-key");
    }

    @Test
    void testPostPlanFeatureRule() {
        PlanFeatureRuleRequest request = new PlanFeatureRuleRequest();
        PlanFeatureRuleDto dto = new PlanFeatureRuleDto();
        when(planFeatureRuleService.createPlanFeatureRule(accountId, request)).thenReturn(dto);

        ResponseEntity<ApiResponse<PlanFeatureRuleDto>> response = monetizationRulesController.postPlanFeatureRule(userContext, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals(dto, response.getBody().getData());
        verify(planFeatureRuleService).createPlanFeatureRule(accountId, request);
    }

    @Test
    void testDeletePlanFeatureRule() {
        String planUuid = UUID.randomUUID().toString();
        String featureUuid = UUID.randomUUID().toString();

        ResponseEntity<ApiResponse<Void>> response = monetizationRulesController.deletePlanFeatureRule(userContext, planUuid, featureUuid);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        verify(planFeatureRuleService).deletePlanFeatureRule(accountId, featureUuid, planUuid);
    }

    @Test
    void testGetPlanFeatureRule() {
        String planUuid = UUID.randomUUID().toString();
        String featureUuid = UUID.randomUUID().toString();
        PlanFeatureRuleDto dto = new PlanFeatureRuleDto();
        when(planFeatureRuleService.getPlanFeatureRule(accountId, featureUuid, planUuid)).thenReturn(dto);

        ResponseEntity<ApiResponse<PlanFeatureRuleDto>> response = monetizationRulesController.getPlanFeatureRule(userContext, planUuid, featureUuid);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals(dto, response.getBody().getData());
        verify(planFeatureRuleService).getPlanFeatureRule(accountId, featureUuid, planUuid);
    }

    @Test
    void testUpdatePlanFeatureRule() {
        PlanFeatureRuleRequest request = new PlanFeatureRuleRequest();
        PlanFeatureRuleDto dto = new PlanFeatureRuleDto();
        when(planFeatureRuleService.updatePlanFeatureRule(accountId, request)).thenReturn(dto);

        ResponseEntity<ApiResponse<PlanFeatureRuleDto>> response = monetizationRulesController.updatePlanFeatureRule(userContext, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals(dto, response.getBody().getData());
        verify(planFeatureRuleService).updatePlanFeatureRule(accountId, request);
    }

    @Test
    void testUpdatePlanFeatureRuleDiff() {
        String planUuid = UUID.randomUUID().toString();
        PlanFeatureLinkedDiffRequest request = new PlanFeatureLinkedDiffRequest();
        PlanFeatureLinkedDiffResponse responseDto = new PlanFeatureLinkedDiffResponse();
        when(planFeatureRuleService.addRemovePlanFeatureRulesByDiff(accountId, request, planUuid)).thenReturn(responseDto);

        ResponseEntity<ApiResponse<PlanFeatureLinkedDiffResponse>> response = monetizationRulesController.updatePlanFeatureRuleDiff(userContext, planUuid, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals(responseDto, response.getBody().getData());
        verify(planFeatureRuleService).addRemovePlanFeatureRulesByDiff(accountId, request, planUuid);
    }
}
