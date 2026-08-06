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
package com.tansoflow.tansocore.service.client;

import com.tansoflow.tansocore.model.credit.CreditPurchaseResult;
import com.tansoflow.tansocore.model.credit.request.CreditPurchaseRequest;

public interface CreditPurchaseService {

    /**
     * Buys credits at the current price book rate. With a payment method
     * (supplied or on file): off-session charge, grant on success. Without
     * one, or when the charge is declined: hosted-checkout fallback — the
     * result carries checkoutUrl + checkoutSessionId and completed=false.
     */
    CreditPurchaseResult purchase(CreditPurchaseRequest request, String customerReferenceId, String accountId);
}
