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
package com.tansoflow.tansocore.service.internal.analytics.implementation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.completions.CompletionUsage;
import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.AiInsight;
import com.tansoflow.tansocore.mapper.analytics.AiInsightMapper;
import com.tansoflow.tansocore.model.analytics.AiInsightDto;
import com.tansoflow.tansocore.model.analytics.AiInsightSeverity;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.AiCsvUploadRepository;
import com.tansoflow.tansocore.repository.AiInsightRepository;
import com.tansoflow.tansocore.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiInsightServiceImplTest {

    @Mock
    private AiInsightRepository aiInsightRepository;

    @Mock
    private AiCsvUploadRepository aiCsvUploadRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private AiInsightMapper aiInsightMapper;

    @Mock
    private OpenAIClient openAIClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AiInsightServiceImpl aiInsightService;

    private UUID accountId;
    private Account account;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        account = new Account();
        account.setId(accountId);
    }

    // ---- listInsights ----

    @Test
    void testListInsights_ReturnsMappedDtos() {
        AiInsight entity = new AiInsight();
        entity.setId(UUID.randomUUID());
        entity.setSeverity(AiInsightSeverity.WARNING);
        entity.setTitle("Test insight");
        entity.setDescription("desc");
        entity.setAccount(account);

        List<AiInsight> entities = List.of(entity);

        AiInsightDto dto = AiInsightDto.builder()
                .severity(AiInsightSeverity.WARNING)
                .title("Test insight")
                .description("desc")
                .build();

        when(aiInsightRepository.findByAccount_IdOrderByCreatedAtDesc(accountId)).thenReturn(entities);
        when(aiInsightMapper.toDtoList(entities)).thenReturn(List.of(dto));

        List<AiInsightDto> result = aiInsightService.listInsights(accountId.toString());

        assertEquals(1, result.size());
        assertEquals("Test insight", result.get(0).getTitle());
        verify(aiInsightRepository).findByAccount_IdOrderByCreatedAtDesc(accountId);
        verify(aiInsightMapper).toDtoList(entities);
    }

    @Test
    void testListInsights_EmptyList() {
        when(aiInsightRepository.findByAccount_IdOrderByCreatedAtDesc(accountId)).thenReturn(List.of());
        when(aiInsightMapper.toDtoList(List.of())).thenReturn(List.of());

        List<AiInsightDto> result = aiInsightService.listInsights(accountId.toString());

        assertTrue(result.isEmpty());
    }

    // ---- clearInsights ----

    @Test
    void testClearInsights_DeletesByAccountId() {
        aiInsightService.clearInsights(accountId.toString());

        verify(aiInsightRepository).deleteByAccount_Id(accountId);
    }

    // ---- generateInsights ----

    @Test
    void testGenerateInsights_Success_ReplacesOldInsights() {
        stubEventRepositoryAggregates();

        String jsonResponse = """
                [
                  {
                    "severity": "WARNING",
                    "title": "High cost on chat-completion",
                    "description": "chat-completion has only 40% margin",
                    "category": "margin_analysis",
                    "featureKey": "chat-completion",
                    "customerId": null
                  }
                ]
                """;

        stubOpenAIResponse(jsonResponse, 100, 50);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        AiInsight savedEntity = new AiInsight();
        savedEntity.setId(UUID.randomUUID());
        savedEntity.setSeverity(AiInsightSeverity.WARNING);
        savedEntity.setTitle("High cost on chat-completion");
        savedEntity.setDescription("chat-completion has only 40% margin");
        savedEntity.setCategory("margin_analysis");
        savedEntity.setFeatureKey("chat-completion");
        savedEntity.setAccount(account);

        when(aiInsightMapper.toEntity(any(AiInsightDto.class))).thenReturn(savedEntity);
        when(aiInsightRepository.save(any(AiInsight.class))).thenReturn(savedEntity);

        AiInsightDto savedDto = AiInsightDto.builder()
                .severity(AiInsightSeverity.WARNING)
                .title("High cost on chat-completion")
                .description("chat-completion has only 40% margin")
                .category("margin_analysis")
                .featureKey("chat-completion")
                .build();
        when(aiInsightMapper.toDtoList(any())).thenReturn(List.of(savedDto));

        List<AiInsightDto> result = aiInsightService.generateInsights(accountId.toString());

        assertEquals(1, result.size());
        assertEquals("High cost on chat-completion", result.get(0).getTitle());

        // Verify old insights deleted and new ones saved
        verify(aiInsightRepository).deleteByAccount_Id(accountId);
        verify(aiInsightRepository).save(any(AiInsight.class));

        // Verify account was set on the entity
        ArgumentCaptor<AiInsight> captor = ArgumentCaptor.forClass(AiInsight.class);
        verify(aiInsightRepository).save(captor.capture());
        assertEquals(account, captor.getValue().getAccount());
    }

    @Test
    void testGenerateInsights_MultipleInsights() {
        stubEventRepositoryAggregates();

        String jsonResponse = """
                [
                  {
                    "severity": "CRITICAL",
                    "title": "Negative margin",
                    "description": "Overall margin is -5%",
                    "category": "margin_analysis",
                    "featureKey": null,
                    "customerId": null
                  },
                  {
                    "severity": "POSITIVE",
                    "title": "Strong growth",
                    "description": "Event volume up 25% MoM",
                    "category": "cost_optimization",
                    "featureKey": null,
                    "customerId": null
                  }
                ]
                """;

        stubOpenAIResponse(jsonResponse, 200, 80);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        AiInsight entity = new AiInsight();
        entity.setId(UUID.randomUUID());
        entity.setAccount(account);
        when(aiInsightMapper.toEntity(any(AiInsightDto.class))).thenReturn(entity);
        when(aiInsightRepository.save(any(AiInsight.class))).thenReturn(entity);
        when(aiInsightMapper.toDtoList(any())).thenReturn(List.of(
                AiInsightDto.builder().severity(AiInsightSeverity.CRITICAL).title("Negative margin").build(),
                AiInsightDto.builder().severity(AiInsightSeverity.POSITIVE).title("Strong growth").build()
        ));

        List<AiInsightDto> result = aiInsightService.generateInsights(accountId.toString());

        assertEquals(2, result.size());
        verify(aiInsightRepository).deleteByAccount_Id(accountId);
    }

    @Test
    void testGenerateInsights_OpenAIError_ReturnsFallback() {
        stubEventRepositoryAggregates();

        // OpenAI client throws exception
        var chatService = mock(com.openai.services.blocking.ChatService.class);
        var completionService = mock(com.openai.services.blocking.chat.ChatCompletionService.class);
        when(openAIClient.chat()).thenReturn(chatService);
        when(chatService.completions()).thenReturn(completionService);
        when(completionService.create(any(ChatCompletionCreateParams.class)))
                .thenThrow(new RuntimeException("API unavailable"));

        List<AiInsightDto> result = aiInsightService.generateInsights(accountId.toString());

        // Should return fallback insight
        assertEquals(1, result.size());
        assertEquals(AiInsightSeverity.INFO, result.get(0).getSeverity());
        assertEquals("system", result.get(0).getCategory());
        assertEquals("AI insights temporarily unavailable", result.get(0).getTitle());

        // Should NOT delete old insights for fallback
        verify(aiInsightRepository, never()).deleteByAccount_Id(any());
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void testGenerateInsights_FallbackResponse_SkipsDeleteAndSave() {
        stubEventRepositoryAggregates();

        // Return empty JSON array from OpenAI → empty generated list
        stubOpenAIResponse("[]", 50, 10);

        List<AiInsightDto> result = aiInsightService.generateInsights(accountId.toString());

        assertTrue(result.isEmpty());

        // Empty result should skip delete and save
        verify(aiInsightRepository, never()).deleteByAccount_Id(any());
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void testGenerateInsights_AccountNotFound_Throws() {
        stubEventRepositoryAggregates();

        String jsonResponse = """
                [{"severity": "INFO", "title": "Test", "description": "Test desc", "category": "margin_analysis", "featureKey": null, "customerId": null}]
                """;
        stubOpenAIResponse(jsonResponse, 100, 50);

        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () ->
                aiInsightService.generateInsights(accountId.toString()));
    }

    @Test
    void testGenerateInsights_MarkdownWrappedResponse_ParsedCorrectly() {
        stubEventRepositoryAggregates();

        // OpenAI sometimes wraps JSON in markdown code blocks
        String jsonResponse = """
                ```json
                [{"severity": "POSITIVE", "title": "Healthy margins", "description": "Overall margin is 65%", "category": "margin_analysis", "featureKey": null, "customerId": null}]
                ```""";

        stubOpenAIResponse(jsonResponse, 80, 30);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        AiInsight entity = new AiInsight();
        entity.setId(UUID.randomUUID());
        entity.setAccount(account);
        when(aiInsightMapper.toEntity(any(AiInsightDto.class))).thenReturn(entity);
        when(aiInsightRepository.save(any(AiInsight.class))).thenReturn(entity);
        when(aiInsightMapper.toDtoList(any())).thenReturn(List.of(
                AiInsightDto.builder().severity(AiInsightSeverity.POSITIVE).title("Healthy margins").build()
        ));

        List<AiInsightDto> result = aiInsightService.generateInsights(accountId.toString());

        assertEquals(1, result.size());
        verify(aiInsightRepository).deleteByAccount_Id(accountId);
    }

    @Test
    void testGenerateInsights_UnknownSeverity_DefaultsToInfo() {
        stubEventRepositoryAggregates();

        String jsonResponse = """
                [{"severity": "UNKNOWN_VALUE", "title": "Test", "description": "desc", "category": "margin_analysis", "featureKey": null, "customerId": null}]
                """;

        stubOpenAIResponse(jsonResponse, 100, 50);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        AiInsight entity = new AiInsight();
        entity.setId(UUID.randomUUID());
        entity.setAccount(account);
        when(aiInsightMapper.toEntity(any(AiInsightDto.class))).thenReturn(entity);
        when(aiInsightRepository.save(any(AiInsight.class))).thenReturn(entity);
        when(aiInsightMapper.toDtoList(any())).thenReturn(List.of(
                AiInsightDto.builder().severity(AiInsightSeverity.INFO).title("Test").build()
        ));

        List<AiInsightDto> result = aiInsightService.generateInsights(accountId.toString());

        assertEquals(1, result.size());

        // Verify the DTO passed to mapper had INFO severity (fallback from unknown)
        ArgumentCaptor<AiInsightDto> captor = ArgumentCaptor.forClass(AiInsightDto.class);
        verify(aiInsightMapper).toEntity(captor.capture());
        assertEquals(AiInsightSeverity.INFO, captor.getValue().getSeverity());
    }

    @Test
    void testGenerateInsights_TokenUsageTracked() {
        stubEventRepositoryAggregates();

        String jsonResponse = """
                [{"severity": "INFO", "title": "Test", "description": "desc", "category": "margin_analysis", "featureKey": null, "customerId": null}]
                """;

        stubOpenAIResponse(jsonResponse, 500, 200);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        AiInsight entity = new AiInsight();
        entity.setId(UUID.randomUUID());
        entity.setAccount(account);
        when(aiInsightMapper.toEntity(any(AiInsightDto.class))).thenReturn(entity);
        when(aiInsightRepository.save(any(AiInsight.class))).thenReturn(entity);
        when(aiInsightMapper.toDtoList(any())).thenReturn(List.of(
                AiInsightDto.builder().severity(AiInsightSeverity.INFO).title("Test").tokensUsed(700).build()
        ));

        aiInsightService.generateInsights(accountId.toString());

        // Verify the DTO passed to mapper has token usage set
        ArgumentCaptor<AiInsightDto> captor = ArgumentCaptor.forClass(AiInsightDto.class);
        verify(aiInsightMapper).toEntity(captor.capture());
        AiInsightDto captured = captor.getValue();
        assertEquals(700, captured.getTokensUsed()); // 500 prompt + 200 completion
        assertNotNull(captured.getCostUsd());
    }

    @Test
    void testGenerateInsights_NoUsageData_TokensNull() {
        stubEventRepositoryAggregates();

        String jsonResponse = """
                [{"severity": "INFO", "title": "Test", "description": "desc", "category": "margin_analysis", "featureKey": null, "customerId": null}]
                """;

        stubOpenAIResponseNoUsage(jsonResponse);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        AiInsight entity = new AiInsight();
        entity.setId(UUID.randomUUID());
        entity.setAccount(account);
        when(aiInsightMapper.toEntity(any(AiInsightDto.class))).thenReturn(entity);
        when(aiInsightRepository.save(any(AiInsight.class))).thenReturn(entity);
        when(aiInsightMapper.toDtoList(any())).thenReturn(List.of(
                AiInsightDto.builder().severity(AiInsightSeverity.INFO).title("Test").build()
        ));

        aiInsightService.generateInsights(accountId.toString());

        // Verify no token usage when usage is absent
        ArgumentCaptor<AiInsightDto> captor = ArgumentCaptor.forClass(AiInsightDto.class);
        verify(aiInsightMapper).toEntity(captor.capture());
        assertTrue(captor.getValue().getTokensUsed() == null);
        assertTrue(captor.getValue().getCostUsd() == null);
    }

    // ---- helpers ----

    private void stubEventRepositoryAggregates() {
        // Feature aggregate: [featureId, featureName, featureKey, count, cost, revenue]
        Object[] featureRow = new Object[]{
                UUID.randomUUID(), "Chat Completion", "chat-completion",
                100L, new BigDecimal("5.00"), new BigDecimal("15.00")
        };
        when(eventRepository.aggregateByFeature(eq(accountId), any(), any(), any()))
                .thenReturn(List.<Object[]>of(featureRow));

        // Model aggregate: [model, provider, count, inputTokens, outputTokens, cost]
        Object[] modelRow = new Object[]{
                "gpt-4o-mini", "openai", 80L, 50000L, 20000L, new BigDecimal("3.00")
        };
        when(eventRepository.sumCostGroupedByModel(eq(accountId), any(), any(), any()))
                .thenReturn(List.<Object[]>of(modelRow));

        // Customer aggregate: [customerId, customerName, customerRef, eventCount, cost, revenue]
        Object[] customerRow = new Object[]{
                UUID.randomUUID(), "Acme Corp", "acme-123", 60L, new BigDecimal("4.00"), new BigDecimal("12.00")
        };
        when(eventRepository.aggregateByCustomer(eq(accountId), any(), any(), any()))
                .thenReturn(List.<Object[]>of(customerRow));
    }

    private ChatCompletion mockChatCompletion(String content, CompletionUsage usage) {
        ChatCompletionMessage message = mock(ChatCompletionMessage.class);
        when(message.content()).thenReturn(Optional.of(content));

        ChatCompletion.Choice choice = mock(ChatCompletion.Choice.class);
        when(choice.message()).thenReturn(message);

        ChatCompletion completion = mock(ChatCompletion.class);
        when(completion.choices()).thenReturn(List.of(choice));
        when(completion.usage()).thenReturn(usage != null ? Optional.of(usage) : Optional.empty());

        return completion;
    }

    private CompletionUsage mockUsage(long promptTokens, long completionTokens) {
        CompletionUsage usage = mock(CompletionUsage.class);
        when(usage.promptTokens()).thenReturn(promptTokens);
        when(usage.completionTokens()).thenReturn(completionTokens);
        return usage;
    }

    private void stubOpenAIClientChain(ChatCompletion completion) {
        var chatService = mock(com.openai.services.blocking.ChatService.class);
        var completionService = mock(com.openai.services.blocking.chat.ChatCompletionService.class);
        when(openAIClient.chat()).thenReturn(chatService);
        when(chatService.completions()).thenReturn(completionService);
        when(completionService.create(any(ChatCompletionCreateParams.class))).thenReturn(completion);
    }

    private void stubOpenAIResponse(String content, long promptTokens, long completionTokens) {
        CompletionUsage usage = mockUsage(promptTokens, completionTokens);
        ChatCompletion completion = mockChatCompletion(content, usage);
        stubOpenAIClientChain(completion);
    }

    private void stubOpenAIResponseNoUsage(String content) {
        ChatCompletion completion = mockChatCompletion(content, null);
        stubOpenAIClientChain(completion);
    }
}
