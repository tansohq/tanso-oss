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
package com.tansoflow.tansocore.model.spend;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SpendSettingsDto {
    @Schema(description = "Whether spend may be attributed to named people (PERSON units, the by-person view).")
    private boolean personLevelEnabled;
    @Schema(description = "What staff were told about spend attribution. Required to enable person level.")
    private String workerNotice;
    @Schema(description = "A Slack incoming webhook is stored for alerts. The URL itself is never returned.")
    private boolean slackConfigured;
    @Schema(description = "A generic webhook URL is stored. Never returned.")
    private boolean webhookConfigured;
    private boolean webhookSigned;
    private String alertEmails;
    private boolean digestEnabled;
}
