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
package com.tansoflow.tansocore.integration.spend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Shared bits of talking to a vendor: the UA they ask integrations to send, and turning an error body into one line. */
final class VendorErrors {
    static final String USER_AGENT = "tanso-oss (https://github.com/tansohq/tanso-oss)";
    private static final int MAX_MESSAGE = 200;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private VendorErrors() {
    }

    /** Both vendors wrap errors as {"error": {"message": "..."}}; fall back to the raw body, trimmed. */
    static String message(String body) {
        if (body == null || body.isBlank()) {
            return "(no body)";
        }
        try {
            JsonNode node = MAPPER.readTree(body);
            JsonNode message = node.path("error").path("message");
            if (message.isTextual() && !message.asText().isBlank()) {
                return truncate(message.asText());
            }
        } catch (Exception ignored) {
            // not JSON — fall through to the raw body
        }
        return truncate(body.trim());
    }

    private static String truncate(String s) {
        return s.length() <= MAX_MESSAGE ? s : s.substring(0, MAX_MESSAGE) + "…";
    }

    /** Only a JSON error field (error.message / detail / message), truncated; null for anything else. */
    public static String jsonMessage(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode n = MAPPER.readTree(body);
            String m = n.path("error").path("message").asText(null);
            if (m == null) m = n.path("detail").isTextual() ? n.path("detail").asText() : null;
            if (m == null) m = n.path("message").asText(null);
            if (m == null) m = n.path("error").isTextual() ? n.path("error").asText() : null;
            return m == null ? null : (m.length() > 200 ? m.substring(0, 200) + "…" : m);
        } catch (Exception e) {
            return null;
        }
    }
}
