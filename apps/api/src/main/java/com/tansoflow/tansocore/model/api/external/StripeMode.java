package com.tansoflow.tansocore.model.api.external;

public enum StripeMode {
    NONE,
    PAYMENT_PASS_THROUGH,
    /** @deprecated Use {@link #STRIPE_INTEGRATION} instead. Kept for backward compatibility. */
    @Deprecated FULL_SYNC,
    STRIPE_INTEGRATION,
    STRIPE_DRIVEN;

    /**
     * Returns true when this mode manages Stripe subscriptions, products, prices, and meters
     * (i.e. FULL_SYNC or STRIPE_INTEGRATION).
     */
    public boolean isStripeIntegration() {
        return this == FULL_SYNC || this == STRIPE_INTEGRATION;
    }
}
