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

import com.tansoflow.tansocore.model.spend.SpendAlertDto;
import com.tansoflow.tansocore.model.spend.SpendBudgetDto;
import com.tansoflow.tansocore.model.spend.request.SpendBudgetRequest;

import java.util.List;

public interface SpendBudgetService {
    SpendBudgetDto getBudget(String accountId, String unitId);

    SpendBudgetDto putBudget(String accountId, String unitId, SpendBudgetRequest request);

    void deleteBudget(String accountId, String unitId);

    /** Checks every budget on the account against the current day and month and fires what crossed. Returns what fired. */
    List<SpendAlertDto> evaluate(String accountId);

    List<SpendAlertDto> listAlerts(String accountId, boolean unackedOnly);

    SpendAlertDto ack(String accountId, String alertId, String actor);
}
