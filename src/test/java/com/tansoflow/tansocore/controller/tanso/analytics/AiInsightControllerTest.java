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
package com.tansoflow.tansocore.controller.tanso.analytics;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.analytics.AiInsightDto;
import com.tansoflow.tansocore.model.analytics.AiInsightSeverity;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.service.internal.analytics.AiInsightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiInsightControllerTest {

    @Mock
    private AiInsightService aiInsightService;

    @InjectMocks
    private AiInsightController aiInsightController;

    private UserContext userContext;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        UUID accountId = UUID.randomUUID();
        userContext = new UserContext(accountId.toString(), "test-api-key");
    }

    @Test
    void testListInsights_Success() {
        List<AiInsightDto> insights = List.of(
                AiInsightDto.builder()
                        .severity(AiInsightSeverity.WARNING)
                        .title("High cost feature")
                        .description("Feature X has 45% margin")
                        .category("margin_analysis")
                        .build()
        );
        when(aiInsightService.listInsights(userContext.getAccountId())).thenReturn(insights);

        ResponseEntity<ApiResponse<List<AiInsightDto>>> response = aiInsightController.listInsights(userContext);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(1, response.getBody().getData().size());
        assertEquals("High cost feature", response.getBody().getData().get(0).getTitle());
        verify(aiInsightService).listInsights(userContext.getAccountId());
    }

    @Test
    void testListInsights_EmptyList() {
        when(aiInsightService.listInsights(userContext.getAccountId())).thenReturn(Collections.emptyList());

        ResponseEntity<ApiResponse<List<AiInsightDto>>> response = aiInsightController.listInsights(userContext);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertTrue(response.getBody().getData().isEmpty());
    }

    @Test
    void testGenerateInsights_Success() {
        List<AiInsightDto> insights = List.of(
                AiInsightDto.builder()
                        .severity(AiInsightSeverity.CRITICAL)
                        .title("Negative margin on GPT-4")
                        .description("GPT-4 usage is costing more than revenue")
                        .category("cost_optimization")
                        .build()
        );
        when(aiInsightService.generateInsights(userContext.getAccountId())).thenReturn(insights);

        ResponseEntity<ApiResponse<List<AiInsightDto>>> response = aiInsightController.generateInsights(userContext);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(1, response.getBody().getData().size());
        verify(aiInsightService).generateInsights(userContext.getAccountId());
    }

    @Test
    void testClearInsights_Success() {
        doNothing().when(aiInsightService).clearInsights(userContext.getAccountId());

        ResponseEntity<ApiResponse<Void>> response = aiInsightController.clearInsights(userContext);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertNull(response.getBody().getData());
        verify(aiInsightService).clearInsights(userContext.getAccountId());
    }
}
