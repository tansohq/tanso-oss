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
package com.tansoflow.tansocore.controller.tanso.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.model.api.external.StripeMode;
import com.tansoflow.tansocore.model.event.service.StripeModeChangedEvent;
import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.service.internal.audit.AuditHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/tanso/account-settings")
@PreAuthorize("hasRole('TANSO_UI')")
@Tag(name = "Account Settings", description = "Account settings management")
public class AccountSettingsController {
    private final AccountSettingRepository accountSettingRepository;
    private final AuditHelper auditHelper;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @GetMapping
    @Operation(summary = "Get account settings", description = "Returns the current account settings including Stripe mode")
    public ResponseEntity<ApiResponse<AccountSettingDto>> getAccountSettings(
            @AuthenticationPrincipal UserContext userContext) {
        AccountSetting setting = accountSettingRepository.findAccountSettingById(
                UUID.fromString(userContext.getAccountId()));

        AccountSettingDto dto = toDto(setting);
        return ResponseEntity.ok(ApiResponse.<AccountSettingDto>builder()
                .success(true)
                .data(dto)
                .build());
    }

    @PatchMapping
    @Transactional
    @Operation(summary = "Update account settings", description = "Updates account settings such as Stripe mode")
    public ResponseEntity<ApiResponse<AccountSettingDto>> updateAccountSettings(
            @AuthenticationPrincipal UserContext userContext,
            @Valid @RequestBody UpdateAccountSettingRequest request) {
        AccountSetting setting = accountSettingRepository.findAccountSettingById(
                UUID.fromString(userContext.getAccountId()));

        if (request.getStripeMode() != null && request.getStripeMode() != setting.getStripeMode()) {
            StripeMode oldMode = setting.getStripeMode();
            setting.setStripeMode(request.getStripeMode());

            UUID actorUserId = userContext.getUserId() != null ? UUID.fromString(userContext.getUserId()) : null;
            UUID actorAccountId = UUID.fromString(userContext.getAccountId());
            auditHelper.audit("STRIPE_MODE_CHANGED", actorUserId, actorAccountId,
                    "AccountSetting", actorAccountId.toString(),
                    oldMode + " -> " + request.getStripeMode());
            eventPublisher.publishEvent(new StripeModeChangedEvent(actorAccountId, oldMode, request.getStripeMode()));
        }
        if (request.getCurrency() != null) {
            String currencyCode = request.getCurrency().toUpperCase();
            try {
                java.util.Currency.getInstance(currencyCode);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid currency code: " + currencyCode);
            }
            setting.setCurrency(currencyCode);
        }
        if (request.getStripeCheckoutSuccessUrl() != null) {
            setting.setStripeCheckoutSuccessUrl(request.getStripeCheckoutSuccessUrl());
        }
        if (request.getStripeCheckoutCancelUrl() != null) {
            setting.setStripeCheckoutCancelUrl(request.getStripeCheckoutCancelUrl());
        }
        if (request.getDefaultCostConfig() != null) {
            DefaultCostConfigDto dcc = request.getDefaultCostConfig();
            if (dcc.getModelCosts() != null) {
                dcc.getModelCosts().values().forEach(rate -> {
                    if (rate.getInput() != null && rate.getInput().compareTo(BigDecimal.ZERO) < 0)
                        throw new IllegalArgumentException("Model input cost rates must be non-negative");
                    if (rate.getOutput() != null && rate.getOutput().compareTo(BigDecimal.ZERO) < 0)
                        throw new IllegalArgumentException("Model output cost rates must be non-negative");
                });
            }
            if (dcc.getDefaultInputCostPerUnit() != null && dcc.getDefaultInputCostPerUnit().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Default input cost per unit must be non-negative");
            }
            if (dcc.getDefaultOutputCostPerUnit() != null && dcc.getDefaultOutputCostPerUnit().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Default output cost per unit must be non-negative");
            }
            boolean isEmpty = dcc.getModelCosts() == null && dcc.getDefaultInputCostPerUnit() == null
                    && dcc.getDefaultOutputCostPerUnit() == null && dcc.getCostUnit() == null;
            setting.setDefaultCostConfig(isEmpty ? null : objectMapper.convertValue(dcc, Map.class));
        }

        accountSettingRepository.save(setting);

        AccountSettingDto dto = toDto(setting);
        return ResponseEntity.ok(ApiResponse.<AccountSettingDto>builder()
                .success(true)
                .data(dto)
                .build());
    }

    private AccountSettingDto toDto(AccountSetting setting) {
        AccountSettingDto dto = new AccountSettingDto();
        dto.setStripeMode(setting.getStripeMode());
        dto.setStripeEnabled(setting.isStripeEnabled());
        dto.setStripeCheckoutSuccessUrl(setting.getStripeCheckoutSuccessUrl());
        dto.setStripeCheckoutCancelUrl(setting.getStripeCheckoutCancelUrl());
        dto.setCurrency(setting.getCurrency());
        if (setting.getDefaultCostConfig() != null) {
            dto.setDefaultCostConfig(objectMapper.convertValue(
                    setting.getDefaultCostConfig(), DefaultCostConfigDto.class));
        }
        return dto;
    }

    @Data
    public static class AccountSettingDto {
        private StripeMode stripeMode;
        private boolean stripeEnabled;
        private String stripeCheckoutSuccessUrl;
        private String stripeCheckoutCancelUrl;
        private String currency;
        private DefaultCostConfigDto defaultCostConfig;
    }

    @Data
    public static class UpdateAccountSettingRequest {
        private StripeMode stripeMode;
        private String currency;
        private String stripeCheckoutSuccessUrl;
        private String stripeCheckoutCancelUrl;
        private DefaultCostConfigDto defaultCostConfig;
    }

    @Data
    public static class DefaultCostConfigDto {
        private Map<String, ModelCostRateDto> modelCosts;
        private BigDecimal defaultInputCostPerUnit;
        private BigDecimal defaultOutputCostPerUnit;
        private String costUnit;
    }

    @Data
    public static class ModelCostRateDto {
        private BigDecimal input;
        private BigDecimal output;
    }
}
