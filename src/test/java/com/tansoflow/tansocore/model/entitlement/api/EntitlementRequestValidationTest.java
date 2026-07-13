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
package com.tansoflow.tansocore.model.entitlement.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntitlementRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testEntitlementEvaluationRequest_Valid() {
        EntitlementEvaluationRequest request = new EntitlementEvaluationRequest();
        request.setCustomerReferenceId("cust_123");
        request.setFeatureKey("llm.generate");

        Set<ConstraintViolation<EntitlementEvaluationRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void testEntitlementEvaluationRequest_BlankFields() {
        EntitlementEvaluationRequest request = new EntitlementEvaluationRequest();
        request.setCustomerReferenceId("");
        request.setFeatureKey(" ");

        Set<ConstraintViolation<EntitlementEvaluationRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("customerReferenceId")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("featureKey")));
    }

    @Test
    void testEntitlementEvaluationRequest_SizeExceeded() {
        EntitlementEvaluationRequest request = new EntitlementEvaluationRequest();
        request.setCustomerReferenceId("a".repeat(129));
        request.setFeatureKey("b".repeat(129));

        Set<ConstraintViolation<EntitlementEvaluationRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("customerReferenceId")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("featureKey")));
    }
}
