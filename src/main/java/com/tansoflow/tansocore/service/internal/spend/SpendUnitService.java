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
package com.tansoflow.tansocore.service.internal.spend;

import com.tansoflow.tansocore.model.spend.SpendAttributionRuleDto;
import com.tansoflow.tansocore.model.spend.SpendUnitDto;
import com.tansoflow.tansocore.model.spend.request.SpendAttributionRuleRequest;
import com.tansoflow.tansocore.model.spend.request.SpendUnitRequest;

import java.util.List;

public interface SpendUnitService {
    List<SpendUnitDto> listUnits(String accountId);

    SpendUnitDto createUnit(String accountId, SpendUnitRequest request);

    SpendUnitDto updateUnit(String accountId, String unitId, SpendUnitRequest request);

    /** Removes the unit, its rules and budget; children are re-parented to the unit's parent. */
    void deleteUnit(String accountId, String unitId);

    List<SpendAttributionRuleDto> listRules(String accountId);

    SpendAttributionRuleDto createRule(String accountId, SpendAttributionRuleRequest request);

    void deleteRule(String accountId, String ruleId);
}
