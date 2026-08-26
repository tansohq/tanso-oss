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
package com.tansoflow.tansocore.model.spend.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;

@Data
public class SpendSettingsRequest {
    private Boolean personLevelEnabled;
    private String workerNotice;
    @ToString.Exclude
    @Schema(description = "Slack incoming webhook URL (https://hooks.slack.com/services/…). Stored encrypted. Empty string removes it; null leaves it alone.")
    private String slackWebhookUrl;
    @ToString.Exclude
    @Schema(description = "Generic webhook URL (https). Every alert and digest is POSTed as JSON with X-Tanso-Event. Stored encrypted. Empty string removes it; null leaves it alone.")
    private String webhookUrl;
    @ToString.Exclude
    @Schema(description = "Signing secret: requests carry X-Tanso-Signature: sha256=HMAC-SHA256(secret, body). Empty string removes it; null leaves it alone.")
    private String webhookSecret;
    @Schema(description = "Comma-separated recipients for alerts and the digest. Empty string removes them; null leaves them alone.")
    private String alertEmails;
    @Schema(description = "Send the weekly digest on Monday 08:00 UTC.")
    private Boolean digestEnabled;
}
