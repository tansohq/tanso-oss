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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.model.client.ClientFeatureDto;
import com.tansoflow.tansocore.model.client.ClientPlanDto;
import com.tansoflow.tansocore.model.client.ClientPlanFeatureLinkedDto;
import com.tansoflow.tansocore.model.credit.CreditFeatureWeightDto;
import com.tansoflow.tansocore.model.credit.CreditPriceDto;
import com.tansoflow.tansocore.model.credit.PlanCreditAllocationDto;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.service.client.ClientPlanService;
import com.tansoflow.tansocore.service.internal.monetization.CreditPriceService;
import com.tansoflow.tansocore.service.internal.monetization.CreditWeightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicCatalogServiceImplTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AccountSettingRepository accountSettingRepository;
    @Mock
    private ClientPlanService clientPlanService;
    @Mock
    private CreditWeightService creditWeightService;
    @Mock
    private CreditPriceService creditPriceService;

    @InjectMocks
    private PublicCatalogServiceImpl service;

    private final UUID accountId = UUID.randomUUID();
    private Account account;
    private AccountSetting settings;

    @BeforeEach
    void setUp() {
        account = new Account();
        account.setId(accountId);
        account.setName("Acme AI");
        account.setSlug("acme");

        settings = new AccountSetting();
        settings.setAccounts(account);
        settings.setCurrency("USD");
        settings.setPublicCatalogEnabled(true);
        settings.setAgentSignupEnabled(true);

        lenient().when(accountRepository.findBySlug("acme")).thenReturn(Optional.of(account));
        lenient().when(accountSettingRepository.findAccountSettingById(accountId)).thenReturn(settings);
        lenient().when(creditPriceService.getCurrentPrices(accountId.toString())).thenReturn(List.of());
        lenient().when(creditWeightService.getWeights(accountId.toString())).thenReturn(List.of());
        lenient().when(clientPlanService.retrieveActivePlansWithPricing(accountId.toString()))
                .thenReturn(List.of());
    }

    private ClientPlanFeatureLinkedDto plan(String key, String name, BigDecimal price, UUID planId) {
        ClientPlanDto planDto = new ClientPlanDto();
        planDto.setId(planId);
        planDto.setKey(key);
        planDto.setName(name);
        planDto.setPriceAmount(price);
        planDto.setCurrency("USD");
        planDto.setIntervalMonths(1);

        ClientFeatureDto feature = new ClientFeatureDto();
        feature.setId(UUID.randomUUID());
        feature.setKey("ai.chat");
        feature.setName("AI Chat");
        feature.setPricingType("usage_based");

        PlanCreditAllocationDto allocation = new PlanCreditAllocationDto();
        allocation.setDenomination("credits");
        allocation.setCreditAmount(new BigDecimal("500"));

        ClientPlanFeatureLinkedDto dto = new ClientPlanFeatureLinkedDto();
        dto.setPlan(planDto);
        dto.setFeatures(List.of(feature));
        dto.setCreditAllocations(List.of(allocation));
        return dto;
    }

    @Test
    void unknownSlugAndDisabledCatalogAreIndistinguishable404s() {
        when(accountRepository.findBySlug("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.buildCatalog("missing", "http://x"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No catalog at this address");

        settings.setPublicCatalogEnabled(false);
        assertThatThrownBy(() -> service.buildCatalog("acme", "http://x"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No catalog at this address");
    }

    @Test
    void catalogValidatesAgainstAgentServeSchema() throws Exception {
        UUID defaultPlanId = UUID.randomUUID();
        settings.setAgentSignupDefaultPlanId(defaultPlanId);
        when(clientPlanService.retrieveActivePlansWithPricing(accountId.toString()))
                .thenReturn(List.of(
                        plan("free", "Free", BigDecimal.ZERO, defaultPlanId),
                        plan("pro", "Pro", new BigDecimal("99.00"), UUID.randomUUID())));

        CreditFeatureWeightDto weight = new CreditFeatureWeightDto();
        weight.setFeatureKey("ai.chat");
        weight.setModel("gpt-4.1");
        weight.setCreditsPerUnit(new BigDecimal("8"));
        weight.setEffectiveFrom(Instant.now().minusSeconds(60));
        when(creditWeightService.getWeights(accountId.toString())).thenReturn(List.of(weight));

        CreditPriceDto price = new CreditPriceDto();
        price.setDenomination("credits");
        price.setPricePerCredit(new BigDecimal("0.01"));
        price.setCurrency("USD");
        when(creditPriceService.getCurrentPrices(accountId.toString())).thenReturn(List.of(price));

        Map<String, Object> catalog = service.buildCatalog("acme", "https://billing.acme.ai");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.valueToTree(catalog);
        try (InputStream schemaStream = getClass().getResourceAsStream("/pricing.schema.json")) {
            JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                    .getSchema(schemaStream);
            Set<ValidationMessage> violations = schema.validate(node);
            assertThat(violations).isEmpty();
        }

        assertThat(node.get("revenue_model").get("type").asText()).isEqualTo("credit");
        assertThat(node.get("revenue_model").get("agent_onboarding").asBoolean()).isTrue();
        assertThat(node.get("credits").get("weight_table").get("ai.chat:gpt-4.1").decimalValue())
                .isEqualByComparingTo("8");
        // Only the signup default plan carries the provisioning URL
        JsonNode plans = node.get("plans");
        assertThat(plans.get(0).get("trial").get("api_provisioning_url").asText())
                .isEqualTo("https://billing.acme.ai/public/v1/catalog/acme/signup");
        assertThat(plans.get(1).has("trial")).isFalse();
    }

    @Test
    void scheduledWeightsAreExcludedAndLatestEffectiveWins() {
        CreditFeatureWeightDto oldRow = new CreditFeatureWeightDto();
        oldRow.setFeatureKey("ai.chat");
        oldRow.setCreditsPerUnit(new BigDecimal("5"));
        oldRow.setEffectiveFrom(Instant.now().minusSeconds(600));
        CreditFeatureWeightDto newRow = new CreditFeatureWeightDto();
        newRow.setFeatureKey("ai.chat");
        newRow.setCreditsPerUnit(new BigDecimal("6"));
        newRow.setEffectiveFrom(Instant.now().minusSeconds(60));
        CreditFeatureWeightDto scheduled = new CreditFeatureWeightDto();
        scheduled.setFeatureKey("ai.chat");
        scheduled.setCreditsPerUnit(new BigDecimal("9"));
        scheduled.setEffectiveFrom(Instant.now().plusSeconds(3600));
        when(creditWeightService.getWeights(accountId.toString()))
                .thenReturn(List.of(scheduled, oldRow, newRow));
        when(clientPlanService.retrieveActivePlansWithPricing(accountId.toString()))
                .thenReturn(List.of(plan("free", "Free", BigDecimal.ZERO, UUID.randomUUID())));

        Map<String, Object> catalog = service.buildCatalog("acme", "http://x");

        @SuppressWarnings("unchecked")
        Map<String, Object> credits = (Map<String, Object>) catalog.get("credits");
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> table = (Map<String, BigDecimal>) credits.get("weight_table");
        assertThat(table.get("ai.chat")).isEqualByComparingTo("6");
    }
}
