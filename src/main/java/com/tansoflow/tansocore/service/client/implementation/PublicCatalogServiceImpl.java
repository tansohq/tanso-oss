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
package com.tansoflow.tansocore.service.client.implementation;

import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.model.client.ClientFeatureDto;
import com.tansoflow.tansocore.model.client.ClientPlanFeatureLinkedDto;
import com.tansoflow.tansocore.model.credit.CreditFeatureWeightDto;
import com.tansoflow.tansocore.model.credit.CreditPriceDto;
import com.tansoflow.tansocore.model.credit.PlanCreditAllocationDto;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.service.client.ClientPlanService;
import com.tansoflow.tansocore.service.client.PublicCatalogService;
import com.tansoflow.tansocore.service.internal.monetization.CreditPriceService;
import com.tansoflow.tansocore.service.internal.monetization.CreditWeightService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PublicCatalogServiceImpl implements PublicCatalogService {

    public static final String SCHEMA_URL =
            "https://raw.githubusercontent.com/katrinalaszlo/agent-serve/main/schema/pricing.schema.json";

    private final AccountRepository accountRepository;
    private final AccountSettingRepository accountSettingRepository;
    private final ClientPlanService clientPlanService;
    private final CreditWeightService creditWeightService;
    private final CreditPriceService creditPriceService;

    @Value("${app.mcp.enabled:false}")
    private boolean mcpEnabled;

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> buildCatalog(String slug, String baseUrl) {
        Account account = accountRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("No catalog at this address"));
        AccountSetting settings = accountSettingRepository.findAccountSettingById(account.getId());
        if (settings == null || !settings.isPublicCatalogEnabled()) {
            // Indistinguishable from an unknown slug on purpose
            throw new ResourceNotFoundException("No catalog at this address");
        }

        String accountId = account.getId().toString();
        List<ClientPlanFeatureLinkedDto> plans = clientPlanService.retrieveActivePlansWithPricing(accountId);
        List<CreditPriceDto> currentPrices = creditPriceService.getCurrentPrices(accountId);
        Map<String, BigDecimal> weightTable = currentWeightTable(accountId);

        boolean anyCredits = plans.stream()
                .anyMatch(p -> p.getCreditAllocations() != null && !p.getCreditAllocations().isEmpty());
        boolean anyUsage = plans.stream()
                .flatMap(p -> p.getFeatures() != null ? p.getFeatures().stream() : java.util.stream.Stream.<ClientFeatureDto>empty())
                .anyMatch(f -> f.getPricingType() != null && !"included".equals(f.getPricingType()));

        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("$schema", SCHEMA_URL);
        catalog.put("product", Map.of(
                "name", account.getName() != null ? account.getName() : slug,
                "vendor", account.getName() != null ? account.getName() : slug,
                "category", "saas",
                "url", baseUrl));
        catalog.put("revenue_model", Map.of(
                "type", anyCredits ? "credit" : (anyUsage ? "usage" : "flat"),
                "billing_frequency", billingFrequencies(plans),
                "agent_onboarding", settings.isAgentSignupEnabled()));
        catalog.put("plans", plans.stream().map(p -> planEntry(p, settings, slug, baseUrl)).toList());

        if (anyCredits || !weightTable.isEmpty() || !currentPrices.isEmpty()) {
            Map<String, Object> credits = new LinkedHashMap<>();
            currentPrices.stream().findFirst()
                    .ifPresent(price -> credits.put("currency_name", price.getDenomination()));
            if (!weightTable.isEmpty()) {
                credits.put("weight_table", weightTable);
            }
            catalog.put("credits", credits);
        }

        catalog.put("governance", Map.of(
                "per_key_limits", true,
                "budget_controls", settings.getAgentMaxTopupAmount() != null,
                "spend_alerts", true,
                "burndown_api", true));
        catalog.put("integration", Map.of(
                "api_docs_url", baseUrl + "/swagger-ui.html",
                "openapi_spec_url", baseUrl + "/v3/api-docs",
                "mcp_server", mcpEnabled,
                "auth_methods", List.of("api-key")));
        return catalog;
    }

    private Map<String, Object> planEntry(ClientPlanFeatureLinkedDto dto, AccountSetting settings,
                                          String slug, String baseUrl) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("id", dto.getPlan().getKey());
        plan.put("name", dto.getPlan().getName());
        Integer interval = dto.getPlan().getIntervalMonths();
        plan.put("price", Map.of(
                "amount", dto.getPlan().getPriceAmount() != null ? dto.getPlan().getPriceAmount() : BigDecimal.ZERO,
                "currency", dto.getPlan().getCurrency() != null ? dto.getPlan().getCurrency() : settings.getCurrency(),
                "per", interval != null && interval == 12 ? "year" : "month"));
        if (dto.getFeatures() != null && !dto.getFeatures().isEmpty()) {
            plan.put("features", dto.getFeatures().stream().map(ClientFeatureDto::getKey).toList());
        }
        if (dto.getCreditAllocations() != null && !dto.getCreditAllocations().isEmpty()) {
            Map<String, Object> included = new LinkedHashMap<>();
            for (PlanCreditAllocationDto allocation : dto.getCreditAllocations()) {
                included.put(allocation.getDenomination(), allocation.getCreditAmount());
            }
            plan.put("included_usage", included);
        }
        if (settings.isAgentSignupEnabled()
                && dto.getPlan().getId().equals(settings.getAgentSignupDefaultPlanId())) {
            plan.put("trial", Map.of(
                    "days", 0,
                    "requires_payment_method", false,
                    "api_provisioning_url", baseUrl + "/public/v1/catalog/" + slug + "/signup"));
        }
        return plan;
    }

    private List<String> billingFrequencies(List<ClientPlanFeatureLinkedDto> plans) {
        List<String> frequencies = new ArrayList<>();
        for (ClientPlanFeatureLinkedDto p : plans) {
            Integer interval = p.getPlan().getIntervalMonths();
            String frequency = interval != null && interval == 12 ? "annual" : "monthly";
            if (!frequencies.contains(frequency)) {
                frequencies.add(frequency);
            }
        }
        return frequencies.isEmpty() ? List.of("monthly") : frequencies;
    }

    /**
     * Latest effective weight per (featureKey, model), keyed "featureKey" for
     * the feature default row and "featureKey:model" for model rows.
     */
    private Map<String, BigDecimal> currentWeightTable(String accountId) {
        Instant now = Instant.now();
        Map<String, BigDecimal> table = new LinkedHashMap<>();
        List<CreditFeatureWeightDto> rows = new ArrayList<>(creditWeightService.getWeights(accountId));
        rows.sort(java.util.Comparator.comparing(CreditFeatureWeightDto::getEffectiveFrom));
        for (CreditFeatureWeightDto row : rows) {
            if (row.getEffectiveFrom().isAfter(now)) continue;
            String key = row.getModel() != null
                    ? row.getFeatureKey() + ":" + row.getModel()
                    : row.getFeatureKey();
            table.put(key, row.getCreditsPerUnit());
        }
        return table;
    }
}
