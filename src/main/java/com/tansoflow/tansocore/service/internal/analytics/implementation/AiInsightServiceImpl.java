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

import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.AiCsvUpload;
import com.tansoflow.tansocore.entity.AiInsight;
import com.tansoflow.tansocore.mapper.analytics.AiInsightMapper;
import com.tansoflow.tansocore.model.analytics.AiInsightDto;
import com.tansoflow.tansocore.model.analytics.AiInsightSeverity;
import com.tansoflow.tansocore.model.event.events.type.EventType;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.AiCsvUploadRepository;
import com.tansoflow.tansocore.repository.AiInsightRepository;
import com.tansoflow.tansocore.repository.EventRepository;
import com.tansoflow.tansocore.service.internal.analytics.AiInsightService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiInsightServiceImpl implements AiInsightService {

    private final AiInsightRepository aiInsightRepository;
    private final AiCsvUploadRepository aiCsvUploadRepository;
    private final AccountRepository accountRepository;
    private final EventRepository eventRepository;
    private final AiInsightMapper aiInsightMapper;
    private final OpenAIClient openAIClient;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<AiInsightDto> listInsights(String accountId) {
        UUID accountUuid = UUID.fromString(accountId);
        List<AiInsight> insights = aiInsightRepository.findByAccount_IdOrderByCreatedAtDesc(accountUuid);
        return aiInsightMapper.toDtoList(insights);
    }

    @Override
    @Transactional
    public List<AiInsightDto> generateInsights(String accountId) {
        UUID accountUuid = UUID.fromString(accountId);
        Instant end = Instant.now();
        Instant start = end.minus(30, ChronoUnit.DAYS);
        Collection<EventType> eventTypes = List.of(EventType.CLIENT_TRACKED);

        // Gather aggregated data and call OpenAI BEFORE deleting old insights
        String[] prompts = buildPrompt(accountUuid, eventTypes, start, end);
        List<AiInsightDto> generated = callOpenAI(prompts[0], prompts[1]);

        // Only replace old insights if generation succeeded with real results
        if (generated.isEmpty() || (generated.size() == 1 && "system".equals(generated.getFirst().getCategory()))) {
            return generated;
        }

        Account account = accountRepository.findById(accountUuid)
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountUuid));

        aiInsightRepository.deleteByAccount_Id(accountUuid);

        List<AiInsight> saved = new ArrayList<>();
        for (AiInsightDto dto : generated) {
            AiInsight entity = aiInsightMapper.toEntity(dto);
            entity.setAccount(account);
            saved.add(aiInsightRepository.save(entity));
        }

        return aiInsightMapper.toDtoList(saved);
    }

    @Override
    @Transactional
    public void clearInsights(String accountId) {
        UUID accountUuid = UUID.fromString(accountId);
        aiInsightRepository.deleteByAccount_Id(accountUuid);
    }

    private String[] buildPrompt(UUID accountId, Collection<EventType> eventTypes, Instant start, Instant end) {
        // Feature breakdown
        List<Object[]> featureRows = eventRepository.aggregateByFeature(accountId, eventTypes, start, end);
        StringBuilder featureSection = new StringBuilder();
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        long totalEvents = 0;

        for (Object[] row : featureRows) {
            String featureName = row[1] != null ? (String) row[1] : (row[2] != null ? (String) row[2] : "unknown");
            long count = (Long) row[3];
            BigDecimal cost = (BigDecimal) row[4];
            BigDecimal revenue = (BigDecimal) row[5];
            BigDecimal margin = revenue.compareTo(BigDecimal.ZERO) > 0
                    ? revenue.subtract(cost).divide(revenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
            featureSection.append(String.format("- %s: %d events, $%s cost, $%s revenue, %.1f%% margin%n",
                    featureName, count, cost.toPlainString(), revenue.toPlainString(), margin.doubleValue()));
            totalCost = totalCost.add(cost);
            totalRevenue = totalRevenue.add(revenue);
            totalEvents += count;
        }

        // Model breakdown
        List<Object[]> modelRows = eventRepository.sumCostGroupedByModel(accountId, eventTypes, start, end);
        StringBuilder modelSection = new StringBuilder();
        for (Object[] row : modelRows) {
            String model = (String) row[0];
            String provider = row[1] != null ? (String) row[1] : "unknown";
            long count = (Long) row[2];
            BigDecimal cost = (BigDecimal) row[5];
            modelSection.append(String.format("- %s (%s): %d events, $%s cost%n",
                    model, provider, count, cost.toPlainString()));
        }

        // Customer breakdown
        List<Object[]> customerRows = eventRepository.aggregateByCustomer(accountId, eventTypes, start, end);
        StringBuilder customerSection = new StringBuilder();
        for (Object[] row : customerRows) {
            String customerName = row[1] != null ? (String) row[1] : (row[2] != null ? (String) row[2] : row[0].toString());
            BigDecimal cost = (BigDecimal) row[4];
            BigDecimal revenue = (BigDecimal) row[5];
            customerSection.append(String.format("- %s: $%s cost, $%s revenue%n",
                    customerName, cost.toPlainString(), revenue.toPlainString()));
        }

        // Historic CSV data uploaded by user
        var csvUploads = aiCsvUploadRepository.findByAccount_IdOrderByCreatedAtDesc(accountId);
        String csvSection = csvUploads.stream()
                .map(upload -> String.format("File: %s (columns: %s)\n%s",
                        upload.getFileName(), upload.getHeaders(),
                        truncateCsvContent(upload.getCsvContent(), 3000)))
                .collect(Collectors.joining("\n---\n"));

        BigDecimal overallMargin = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                ? totalRevenue.subtract(totalCost).divide(totalRevenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        boolean hasEvents = totalEvents > 0;
        boolean hasCsv = !csvSection.isEmpty();
        String dataContext;
        if (hasEvents && hasCsv) {
            dataContext = "Analyze BOTH the live usage data AND the historic CSV data below. Cross-reference trends between them.";
        } else if (hasCsv) {
            dataContext = "No live usage data is available yet. Analyze the historic CSV data below to provide insights on costs, trends, and opportunities.";
        } else {
            dataContext = "Analyze the following 30-day live usage data.";
        }

        String systemPrompt = "You are a cost optimization analyst for a B2B SaaS company that uses AI models. "
                + "Every insight you generate MUST include a specific recommended action. "
                + "Never state a problem without recommending what to do about it.";

        String userPrompt = String.format("""
                %s
                Provide 3-5 actionable insights. Prioritize the most severe issues first.

                SEVERITY ASSIGNMENT RULES (follow strictly):

                CRITICAL — use ONLY when the data shows actual financial losses:
                  - Feature or customer margin is NEGATIVE (below 0%%)
                  - Overall company margin is negative
                  - Revenue is declining steeply (>50%% drop) threatening business viability
                  - A customer's cost exceeds their revenue (cost > revenue)
                  DO NOT use CRITICAL for: thin-but-positive margins, concentration risk, pricing mismatches, or cost increases that haven't caused losses

                WARNING — use for risks and inefficiencies that are NOT yet losses:
                  - Margins between 0%% and 30%% (thin but not negative)
                  - Revenue concentration where top 2 customers represent >70%% of revenue
                  - Pricing model mismatches (e.g., flat-rate when usage varies 10x+ across customers)
                  - Cost trends growing faster than revenue
                  - Usage declining month-over-month (moderate churn risk)
                  DO NOT use WARNING for: negative margins (use CRITICAL) or healthy metrics (use POSITIVE/INFO)

                POSITIVE — use for genuinely healthy metrics:
                  - Margins above 60%%
                  - Features or customers with strong profitability
                  - Growth opportunities backed by data
                  DO NOT use POSITIVE for: margins below 50%%, or metrics that have risk factors

                INFO — use for neutral observations:
                  - Margins between 30-60%% with no specific risk
                  - General patterns that don't require action
                  DO NOT use INFO for: anything requiring urgent attention (use WARNING or CRITICAL)

                CALIBRATION EXAMPLES:
                  - Feature at -50%% margin -> CRITICAL (actual loss)
                  - Feature at 2.7%% margin -> WARNING (thin but positive, not a loss)
                  - Feature at 67%% margin -> POSITIVE (healthy)
                  - Customer cost $3200, revenue $1200 -> CRITICAL (losing money on this customer)
                  - Customer cost $800, revenue $3600 -> POSITIVE (highly profitable)
                  - Top 2 customers = 86%% of revenue -> WARNING (concentration risk, not a loss)
                  - Overall margin 75%% but usage dropping 75%% over 6 months -> CRITICAL (business viability threat)
                  - Overall margin 25%% -> WARNING (thin margins, not yet a loss)

                PRICING ANALYSIS (when usage data suggests pricing mismatches):
                1. Check usage variance: if the highest-usage customer uses 10x+ more than the lowest on the same plan, flat-rate pricing is wrong
                2. Check value metric alignment: if customers paying per-API-call have wildly different token counts per call, API calls are the wrong value metric
                3. When you find a mismatch, recommend specifically:
                   - The correct value metric (e.g., 'output tokens' not 'API calls')
                   - The pricing model (usage-based, tiered, per-seat, credit-based)
                   - Why this fits the data (cite the specific variance or mismatch numbers)

                LIVE DATA (last 30 days):
                Overall: %d events, $%s total cost, $%s total revenue, %.1f%% margin

                By feature:
                %s
                By model:
                %s
                By customer (top 10):
                %s
                HISTORIC DATA (user-uploaded CSV):
                %s
                Return a JSON array of insights. Each insight must have:
                - "severity": one of "CRITICAL", "WARNING", "POSITIVE", "INFO"
                - "title": short headline
                - "description": 1-2 sentence explanation with specific numbers from the data
                - "category": one of "margin_analysis", "model_comparison", "customer_profitability", "cost_optimization", "pricing_optimization"
                - "featureKey": the relevant feature key if applicable, or null
                - "customerId": the relevant customer if applicable, or null

                IMPORTANT RULES:
                - Use actual numbers from the data, not generic advice.
                - Every description MUST end with a specific action: 'Action: [do X]' where X names the specific model, feature, customer, or pricing change.
                - If historic CSV data is provided, interpret the column names and analyze trends.
                - Return ONLY the JSON array, no markdown wrapping.
                """,
                dataContext,
                totalEvents, totalCost.toPlainString(), totalRevenue.toPlainString(), overallMargin.doubleValue(),
                featureSection, modelSection, customerSection,
                hasCsv ? csvSection : "None uploaded");

        return new String[]{systemPrompt, userPrompt};
    }

    private List<AiInsightDto> callOpenAI(String systemPrompt, String userPrompt) {
        try {
            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model(ChatModel.GPT_4O_MINI)
                    .temperature(0.3)
                    .maxCompletionTokens(1500L)
                    .addSystemMessage(systemPrompt)
                    .addUserMessage(userPrompt)
                    .build();

            ChatCompletion completion = openAIClient.chat().completions().create(params);

            // Extract token usage and cost from the response
            Integer tokensUsed = null;
            BigDecimal costUsd = null;
            if (completion.usage().isPresent()) {
                var usage = completion.usage().get();
                long promptTokens = usage.promptTokens();
                long completionTokens = usage.completionTokens();
                tokensUsed = (int) (promptTokens + completionTokens);
                // gpt-4o-mini: $0.15/M input, $0.60/M output
                costUsd = BigDecimal.valueOf(promptTokens * 0.00000015 + completionTokens * 0.0000006)
                        .setScale(6, RoundingMode.HALF_UP);
            }

            String content = completion.choices().getFirst().message().content().orElse("[]");

            // Strip markdown wrapping if present
            content = content.trim();
            if (content.startsWith("```")) {
                content = content.replaceFirst("```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
            }

            List<Map<String, Object>> rawInsights = objectMapper.readValue(
                    content, new TypeReference<>() {});

            List<AiInsightDto> results = new ArrayList<>();
            for (Map<String, Object> raw : rawInsights) {
                AiInsightSeverity severity;
                try {
                    severity = AiInsightSeverity.valueOf(((String) raw.get("severity")).toUpperCase());
                } catch (Exception e) {
                    log.warn("Unparseable AI insight severity '{}', defaulting to INFO", raw.get("severity"));
                    severity = AiInsightSeverity.INFO;
                }

                results.add(AiInsightDto.builder()
                        .severity(severity)
                        .title((String) raw.get("title"))
                        .description((String) raw.get("description"))
                        .category((String) raw.get("category"))
                        .featureKey((String) raw.get("featureKey"))
                        .customerId((String) raw.get("customerId"))
                        .tokensUsed(tokensUsed)
                        .costUsd(costUsd)
                        .build());
            }
            return results;

        } catch (Exception e) {
            log.error("Failed to generate AI insights: {}", e.getMessage(), e);
            return generateFallbackInsights();
        }
    }

    private String truncateCsvContent(String content, int maxLength) {
        if (content.length() <= maxLength) return content;
        return content.substring(0, maxLength) + "\n... (truncated)";
    }

    private List<AiInsightDto> generateFallbackInsights() {
        return List.of(AiInsightDto.builder()
                .severity(AiInsightSeverity.INFO)
                .title("AI insights temporarily unavailable")
                .description("Could not reach the AI service. Please try again in a few moments.")
                .category("system")
                .build());
    }
}
