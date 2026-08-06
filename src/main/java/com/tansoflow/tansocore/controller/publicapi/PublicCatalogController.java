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
package com.tansoflow.tansocore.controller.publicapi;

import com.tansoflow.tansocore.service.client.PublicCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Unauthenticated discovery surface for buying agents. Served only for
 * accounts that set a slug AND enabled the public catalog; anything else
 * is a 404 that does not confirm the account exists.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/public/v1/catalog")
@Tag(name = "Public Catalog", description = "Machine-readable pricing for buying agents — no authentication")
public class PublicCatalogController {

    private final PublicCatalogService publicCatalogService;

    @GetMapping(value = "/{slug}/pricing.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Machine-readable pricing catalog",
            description = "The account's plans, features, credit weight table, and governance flags in the "
                    + "agent-serve pricing.json format. Raw JSON, not the ApiResponse envelope, so agents can "
                    + "validate it directly against the schema.")
    public ResponseEntity<Map<String, Object>> getPricingCatalog(
            @PathVariable String slug, HttpServletRequest request) {
        String baseUrl = request.getRequestURL().toString()
                .replace(request.getRequestURI(), "");
        return ResponseEntity.ok(publicCatalogService.buildCatalog(slug, baseUrl));
    }
}
