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
package com.tansoflow.tansocore.mcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.exception.ResourceNotFoundException;
import com.tansoflow.tansocore.model.feature.request.FeatureRequest;
import com.tansoflow.tansocore.service.internal.monetization.FeatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class AdminFeatureTools {

    private final FeatureService featureService;
    private final ObjectMapper objectMapper;

    @Tool(description = "List all features in the account. "
            + "Returns every feature with its ID, name, key, description, and enabled status.")
    public String adminListFeatures() {
        try {
            var features = featureService.getFeatures(getAccountId());
            return objectMapper.writeValueAsString(features);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize features\"}";
        }
    }

    @Tool(description = "CREATES a new feature in the product catalog. "
            + "SIDE EFFECT: Permanently creates a feature record. "
            + "Features represent individual capabilities (e.g. 'api_access', 'ai_messages') that can be linked to plans.")
    public String adminCreateFeature(
            @ToolParam(description = "Display name for the feature, e.g. 'API Access'") String name,
            @ToolParam(description = "Unique key for the feature, e.g. 'api_access'") String key,
            @ToolParam(description = "Optional description of the feature", required = false) String description) {
        try {
            FeatureRequest request = new FeatureRequest();
            request.setName(name);
            request.setKey(key);
            request.setDescription(description != null ? description : "");

            var result = featureService.createFeature(getAccountId(), request);
            return objectMapper.writeValueAsString(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "{\"error\": \"invalid_request\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize feature\"}";
        }
    }

    @Tool(description = "UPDATES an existing feature. "
            + "SIDE EFFECT: Modifies the feature record. Only provided fields are updated; "
            + "omitted fields remain unchanged. "
            + "CONSTRAINT: The key must remain unique within the account.")
    public String adminUpdateFeature(
            @ToolParam(description = "The feature ID (UUID)") String featureId,
            @ToolParam(description = "New display name", required = false) String name,
            @ToolParam(description = "New unique key", required = false) String key,
            @ToolParam(description = "New description", required = false) String description,
            @ToolParam(description = "Enable or disable the feature", required = false) Boolean isEnabled) {
        try {
            FeatureRequest request = new FeatureRequest();
            if (name != null) request.setName(name);
            if (key != null) request.setKey(key);
            if (description != null) request.setDescription(description);
            if (isEnabled != null) request.setIsEnabled(isEnabled);

            var result = featureService.updateFeature(getAccountId(), UUID.fromString(featureId), request);
            return objectMapper.writeValueAsString(result);
        } catch (ResourceNotFoundException e) {
            return "{\"error\": \"not_found\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "{\"error\": \"invalid_request\", \"message\": \"" + e.getMessage() + "\"}";
        } catch (JsonProcessingException e) {
            return "{\"error\": \"serialization_error\", \"message\": \"Failed to serialize feature\"}";
        }
    }

    private String getAccountId() {
        UserContext ctx = (UserContext) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return ctx.getAccountId();
    }
}
