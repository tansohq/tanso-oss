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
import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.entity.AccountSetting;
import com.tansoflow.tansocore.model.api.external.StripeMode;
import com.tansoflow.tansocore.repository.AccountRepository;
import com.tansoflow.tansocore.repository.AccountSettingRepository;
import com.tansoflow.tansocore.repository.PlanRepository;
import com.tansoflow.tansocore.service.internal.audit.AuditHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountSettingsControllerMandateTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private PlanRepository planRepository;
    @Mock
    private AccountSettingRepository accountSettingRepository;
    @Mock
    private AuditHelper auditHelper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AccountSettingsController controller;
    private AccountSetting setting;
    private final UUID accountId = UUID.randomUUID();
    private UserContext user;

    @BeforeEach
    void setUp() {
        controller = new AccountSettingsController(accountRepository, planRepository, accountSettingRepository,
                auditHelper, eventPublisher, new ObjectMapper());
        Account account = new Account();
        account.setId(accountId);
        setting = new AccountSetting();
        setting.setAccounts(account);
        setting.setStripeMode(StripeMode.PAYMENT_PASS_THROUGH);
        when(accountSettingRepository.findAccountSettingById(accountId)).thenReturn(setting);
        user = new UserContext(accountId.toString(), null);
    }

    @Test
    void enablingMandatesWithoutACeilingIsRefused() {
        AccountSettingsController.UpdateAccountSettingRequest request = new AccountSettingsController.UpdateAccountSettingRequest();
        request.setAgentSpendMandateEnabled(true);

        assertThatThrownBy(() -> controller.updateAccountSettings(user, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Set agentMaxMandateAmount before enabling the agent spend mandate");
        assertThat(setting.isAgentSpendMandateEnabled()).isFalse();
        verify(accountSettingRepository, never()).save(any());
    }

    @Test
    void theCeilingAndTheFlagCanBeSetInOneRequest() {
        AccountSettingsController.UpdateAccountSettingRequest request = new AccountSettingsController.UpdateAccountSettingRequest();
        request.setAgentMaxMandateAmount(new BigDecimal("200.00"));
        request.setAgentSpendMandateEnabled(true);

        var response = controller.updateAccountSettings(user, request);

        assertThat(setting.isAgentSpendMandateEnabled()).isTrue();
        assertThat(setting.getAgentMaxMandateAmount()).isEqualByComparingTo("200.00");
        assertThat(response.getBody().getData().getAgentMaxMandateAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void theCeilingCannotBeClearedWhileMandatesAreOn() {
        setting.setAgentMaxMandateAmount(new BigDecimal("200.00"));
        setting.setAgentSpendMandateEnabled(true);
        AccountSettingsController.UpdateAccountSettingRequest request = new AccountSettingsController.UpdateAccountSettingRequest();
        request.setAgentMaxMandateAmount(BigDecimal.ZERO);

        assertThatThrownBy(() -> controller.updateAccountSettings(user, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Turn off agentSpendMandateEnabled");
        assertThat(setting.getAgentMaxMandateAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void aNegativeCeilingIsRefused() {
        AccountSettingsController.UpdateAccountSettingRequest request = new AccountSettingsController.UpdateAccountSettingRequest();
        request.setAgentMaxMandateAmount(new BigDecimal("-1"));

        assertThatThrownBy(() -> controller.updateAccountSettings(user, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("agentMaxMandateAmount must not be negative");
    }
}
