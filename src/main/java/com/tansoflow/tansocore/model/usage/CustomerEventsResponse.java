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
package com.tansoflow.tansocore.model.usage;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The individual events behind a usage total. Recorded usage is append-only: a correction is another event, so
 * what is returned here is what was written at the time.
 */
@Getter
@Builder
@Schema(description = "Events a customer recorded, newest first")
public class CustomerEventsResponse {
    private String customerReferenceId;
    private Instant from;
    private Instant to;
    @Schema(description = "True when more events exist before this page's oldest entry; ask again with a higher offset")
    private Boolean hasMore;
    private List<RecordedEvent> events;

    @Getter
    @Builder
    @Schema(description = "One recorded event, as it was written")
    public static class RecordedEvent {
        private String id;
        @Schema(description = "The key the client sent to make the write idempotent. Null when none was sent.")
        private String eventIdempotencyKey;
        private String eventName;
        private String featureKey;
        @Schema(description = "The subscription the event was recorded against. Null when it carried none.")
        private String subscriptionId;
        private BigDecimal usageUnits;
        private String usageUnitType;
        private Instant occurredAt;
    }
}
