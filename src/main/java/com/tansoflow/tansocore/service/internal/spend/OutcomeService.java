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

import com.tansoflow.tansocore.model.spend.OutcomeDto;
import com.tansoflow.tansocore.model.spend.OutcomeSourceDto;
import com.tansoflow.tansocore.model.spend.SpendOutcomeReportDto;
import com.tansoflow.tansocore.model.spend.VendorProbeResultDto;
import com.tansoflow.tansocore.model.spend.VendorSyncResultDto;
import com.tansoflow.tansocore.model.spend.request.OutcomeRequest;
import com.tansoflow.tansocore.model.spend.request.OutcomeSourceRequest;

import java.time.LocalDate;
import java.util.List;

public interface OutcomeService {
    List<OutcomeSourceDto> listSources(String accountId);

    OutcomeSourceDto createSource(String accountId, OutcomeSourceRequest request);

    void deleteSource(String accountId, String sourceId);

    VendorProbeResultDto probe(String accountId, String sourceId);

    /** Pulls [from, to) and upserts. Nulls default to the last 30 days. */
    VendorSyncResultDto sync(String accountId, String sourceId, LocalDate from, LocalDate to);

    void syncAll();

    /** A CI job or script saying something shipped. Same externalId updates in place. */
    OutcomeDto record(String accountId, OutcomeRequest request);

    List<OutcomeDto> recent(String accountId);

    SpendOutcomeReportDto report(String accountId, LocalDate from, LocalDate to);
}
