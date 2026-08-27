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
package com.tansoflow.tansocore.controller.tanso.monetization;

import com.tansoflow.tansocore.model.response.ApiResponse;
import com.tansoflow.tansocore.model.response.Error;
import com.tansoflow.tansocore.model.response.ErrorCode;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mirror of {@code BuildModuleDisabledController} for the other half. When
 * customer billing is switched off, every route it owned answers 404 with a
 * code the console keys on, so a wrong API URL and a disabled module are not
 * confused. The paths listed are exactly the controllers gated on
 * {@code app.modules.monetization.enabled}; {@code /api/v1/client/outcomes} is more
 * specific than the client catch-all and keeps working when internal spend is
 * on.
 */
@Hidden
@RestController
@ConditionalOnProperty(name = "app.modules.monetization.enabled", havingValue = "false")
public class MonetizationModuleDisabledController {
    @RequestMapping({
            "/api/v1/monetization/**",
            "/api/v1/client/**",
            "/api/v1/analytics/**",
            "/api/v1/tanso/events/**",
            "/api/v1/tanso/customers/**",
            "/api/v1/tanso/csv-import/**",
            "/api/v1/data/stripe/**",
            "/public/v1/catalog/**",
            "/public/stripe/**"
    })
    public ResponseEntity<ApiResponse<Void>> disabled() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.<Void>builder().success(false)
                .error(new Error(ErrorCode.MODULE_DISABLED,
                        "Monetization is switched off on this install: APP_MODULES_MONETIZATION_ENABLED=false"))
                .build());
    }
}
