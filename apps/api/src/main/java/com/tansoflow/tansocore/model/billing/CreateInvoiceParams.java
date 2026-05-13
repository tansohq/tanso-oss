package com.tansoflow.tansocore.model.billing;

import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.model.billing.type.InvoiceStatus;
import com.tansoflow.tansocore.model.billing.type.InvoiceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CreateInvoiceParams(
        Subscription subscription,
        LocalDate dueDate,
        InvoiceStatus status,
        Instant periodStart,
        Instant periodEnd,
        BigDecimal amount,
        String currency,
        InvoiceType type
) { }

