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
