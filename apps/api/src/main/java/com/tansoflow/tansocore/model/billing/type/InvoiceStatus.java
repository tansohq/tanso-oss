package com.tansoflow.tansocore.model.billing.type;

public enum InvoiceStatus {
    PAST_DUE,
    DUE,
    PENDING,
    PAID,
    CANCELLED,
    CANCELLED_PROCESSED,
    VOID,
    ADJUSTMENT_OPEN,
    ADJUSTMENT_PAID,
}
