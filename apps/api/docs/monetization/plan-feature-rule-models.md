# Plan Feature Rule JSON Models

The `value` field in the `PlanFeatureRule` entity is a polymorphic JSON object that defines how usage and costs are calculated for a specific feature.

Currently, all plan feature rules should use the `BASE` type. The rule's specific behavior is determined by the `model` field within the JSON `value`.

## Common Fields

| Key | Type | Description |
| :--- | :--- | :--- |
| `model` | String | The pricing model identifier (e.g., `"usage"`, `"graduated"`). |
| `usage_unit_type` | String | The unit being measured (e.g., `"token"`, `"hit"`, `"gb"`). |

---

## 1. Simple Usage Model (`model: "usage"`)

This is the standard model where a fixed rate is applied to all usage units.

### Structure
```json
{
  "model": "usage",
  "usage_unit_type": "token",
  "price_per_unit": 0.0001,
  "cost_rate": 0.00005,
  "cost_per_unit": 0.00004,
  "cost_unit": "USD"
}
```

### Rate Resolution Priority
The system checks for rates in the following order for pricing calculation:
1. `price_per_unit` (Primary - Billing alignment)
2. `cost_rate` (Secondary - Ingestion alignment)
3. `cost_per_unit` (Legacy - Seed/Analytics alignment)

If multiple fields are present, the first one found in the above order will be used as the effective rate.

---

## 2. Graduated Pricing Model (`model: "graduated"`)

This model calculates costs based on tiers of usage. Different rates are applied to different brackets of the total usage.

### Structure
```json
{
  "model": "graduated",
  "usage_unit_type": "api_calls",
  "tiers": [
    {
      "up_to": 1000,
      "price_per_unit": 0.10
    },
    {
      "up_to": 5000,
      "price_per_unit": 0.08
    },
    {
      "up_to": "inf",
      "price_per_unit": 0.05
    }
  ]
}
```

### Calculation Example
If `usageUnits = 1500`:
- First 1000 units: `1000 * 0.10 = 100.00`
- Next 500 units: `500 * 0.08 = 40.00`
- **Total Cost**: `140.00`

---

## Cost Models (`cost_model`)

In addition to the `model` (Pricing Model), you can specify an explicit `cost_model` to define the COGS (Cost of Goods Sold) for a feature. This allows you to track margins by separating what you charge the customer from what you pay to a 3rd party.

If `cost_model` is present, the system uses it to calculate the actual cost (COGS). If `cost_model` is missing, the system falls back to the legacy cost fields in the `SimpleUsageModel` (`cost_rate` or `cost_per_unit`) for cost calculation.

### 1. Simple Cost Model (`cost_model: "simple"`)

| Key | Type | Description |
| :--- | :--- | :--- |
| `cost_model` | String | set to `"simple"`. |
| `cost_per_unit` | Number | The rate you pay to the 3rd party provider. |
| `cost_unit` | String | (Optional) Currency of the cost (e.g., `"USD"`, `"TOKENS"`, `"CREDITS"`). |

#### Example
```json
{
  "model": "usage",
  "usage_unit_type": "token",
  "price_per_unit": 0.0001,
  "cost_model": "simple",
  "cost_per_unit": 0.00004,
  "cost_unit": "USD"
}
```

### Calculation Logic

- **Pricing**: Calculated based on `model` (`usage` or `graduated`).
- **Cost (COGS)**:
    - If `cost_model` is present, it uses that model's calculation logic.
    - If `cost_model` is missing AND the pricing model is `usage`, it falls back to `cost_rate` or `cost_per_unit`.
    - Otherwise, it defaults to `0`.

---

## Integration Notes

- **Real-time Ingestion**: `EventServiceImpl` uses these models to calculate `costAmount` on events at the time of tracking.
- **Analytics**: `AnalyticsServiceImpl` aggregates costs using these rules to calculate customer margins.
- **Invoicing**: `InvoiceServiceImpl` uses these rules to generate line items for usage-based billing in arrears.
