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

import com.tansoflow.tansocore.entity.SpendAttributionRule;
import com.tansoflow.tansocore.entity.SpendBudget;
import com.tansoflow.tansocore.entity.VendorConnection;
import com.tansoflow.tansocore.integration.spend.LiteLlmGateway;
import com.tansoflow.tansocore.model.exception.VendorApiException;
import com.tansoflow.tansocore.model.spend.type.BudgetMode;
import com.tansoflow.tansocore.model.spend.type.VendorProvider;
import com.tansoflow.tansocore.repository.SpendAttributionRuleRepository;
import com.tansoflow.tansocore.repository.VendorConnectionRepository;
import com.tansoflow.tansocore.service.internal.spend.GatewayEnforcementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.modules.build.enabled", havingValue = "true", matchIfMissing = true)
public class GatewayEnforcementServiceImpl implements GatewayEnforcementService {
    private final VendorConnectionRepository connectionRepository;
    private final SpendAttributionRuleRepository ruleRepository;
    private final LiteLlmGateway liteLlm;
    private final Clock clock;

    @Override
    public void apply(SpendBudget budget) {
        List<VendorConnection> gateways = connectionRepository.findAllByAccountIdOrderByCreatedAtAsc(budget.getAccountId())
                .stream().filter(c -> c.getProvider() == VendorProvider.LITELLM).toList();
        List<SpendAttributionRule> rules = ruleRepository.findAllByAccountIdOrderByPriorityAscCreatedAtAsc(budget.getAccountId())
                .stream().filter(r -> r.getSpendUnitId().equals(budget.getSpendUnitId()) && r.getProvider() == VendorProvider.LITELLM).toList();
        if (gateways.isEmpty() || rules.isEmpty()) {
            budget.setEnforcementTarget(null);
            budget.setEnforcedAt(null);
            budget.setEnforcementError(gateways.isEmpty() ? null : "No LiteLLM rule on this unit — add a rule naming its team, key or user");
            return;
        }
        boolean block = budget.getMonthlyMode() == BudgetMode.BLOCK && budget.getMonthlyCents() != null && budget.getMonthlyCents().signum() > 0;
        List<String> targets = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (VendorConnection gateway : gateways) {
            for (SpendAttributionRule rule : rules) {
                try {
                    // A budget that is no longer Block clears the hard limit (max_budget null) rather than leaving a stale one.
                    String target = liteLlm.pushMonthlyBudget(gateway.getAdminKey(), gateway.getScope(), rule.getMatchKind(), rule.getMatchValue(),
                            block ? budget.getMonthlyCents() : null);
                    if (block) {
                        targets.add(target);
                    }
                } catch (VendorApiException | IllegalArgumentException e) {
                    errors.add(rule.getMatchKind() + " " + rule.getMatchValue() + ": " + e.getMessage());
                    log.warn("Gateway push failed for unit {}: {}", budget.getSpendUnitId(), e.getMessage());
                }
            }
        }
        budget.setEnforcementTarget(targets.isEmpty() ? null : String.join(", ", targets));
        budget.setEnforcedAt(targets.isEmpty() ? null : clock.instant());
        budget.setEnforcementError(errors.isEmpty() ? null : String.join("; ", errors));
    }
}
