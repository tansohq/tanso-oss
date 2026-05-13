# Plan Feature Rule Extensibility Guide

This document explains the architectural role of the `type` column in the `plan_feature_rules` table and provides a roadmap for how to extend the system beyond the current `BASE` implementation.

## 1. Architectural Overview

The system uses a two-tier classification for rules:

| Level | Component | Purpose | Examples |
| :--- | :--- | :--- | :--- |
| **Tier 1: Domain** | `type` (Column) | Categorizes the high-level purpose of the rule for database indexing, routing, and filtering. | `BASE`, `QUOTA`, `PERMISSION` |
| **Tier 2: Strategy** | `value.model` (JSON) | Defines the specific logic or algorithm used to evaluate the rule. | `usage`, `graduated`, `flat` |

By keeping these separate, we ensure that adding a new pricing algorithm doesn't require schema changes, while adding a new rule domain remains explicit and performant.

## 2. The Role of `BASE`

Currently, all rules use the `BASE` type. In this context, `BASE` represents **Monetization Rules**. 

- **Primary use**: Defines how usage or access translates into cost/price.
- **Evaluation**: Handled by `RuleCalculationUtil` and `PricingModel` implementations.
- **Logic**: Driven by the `model` field inside the JSON payload.

## 3. Future Rule Types

When the system needs to handle logic that is **not related to monetization**, new types should be introduced. Below are planned categories for future expansion.

### A. `QUOTA` (Hard Limits)
While `BASE` rules can have limits for pricing tiers, a `QUOTA` rule is used for system-level constraints that are not directly billed.
- **Example Use Case**: "Customers on the Free Plan can only have 5 active projects."
- **Why not `BASE`?**: Because there is no price associated; it's a binary "allowed/denied" check based on a fixed number.

### B. `ACCESS_POLICY` (Conditional Access)
Used for rules that depend on account attributes, geography, or time rather than usage.
- **Example Use Case**: "This feature is only available for accounts in the EU region due to GDPR."
- **Why not `BASE`?**: The logic requires inspecting the `Account` context rather than the `Event` usage.

### C. `REPLACEMENT` (Override Logic)
Used for complex migrations or promotional overrides.
- **Example Use Case**: "For the first 3 months, replace the standard AI token rate with a promotional rate."
- **Why not `BASE`?**: It allows the system to store a "hidden" override that takes precedence over the standard plan rules.

## 4. How to Implement a New Type

To add a new rule type, follow these steps:

1.  **Update Enum**: Add the new value to `PlanFeatureRuleType`.
2.  **Define DTO**: If the new type requires a different JSON structure, create a new interface or class that extends/replaces the `PricingModel` logic for that type.
3.  **Service Routing**: Update `PlanFeatureRuleServiceImpl` to handle the specific validation requirements for the new type.
4.  **Orchestrator Integration**: Update the `EntitlementOrchestrator` to understand how the new rule type affects a customer's real-time entitlements.

## 5. Summary of Best Practices

- **Use `BASE` for everything billed**: Even if it's a new complex hybrid model, if it results in a line item on an invoice, it belongs in `BASE`.
- **Use Columns for Filtering**: Only move data from the JSON `value` to a database column if you frequently need to run `WHERE` clauses or `JOIN`s on that specific field across all customers.
- **Keep Types Mutually Exclusive**: A single rule should only have one `type`. If a feature needs both a price (`BASE`) and a hard limit (`QUOTA`), create two separate rules linked to the same feature.
