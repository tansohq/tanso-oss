package com.tansoflow.tansocore.model.credit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@Schema(description = "Data Transfer Object for Credit Transaction (ledger entry)")
public class CreditTransactionDto {
    @Schema(description = "Unique identifier of the transaction")
    private String id;

    @Schema(description = "Credit pool this transaction belongs to")
    private String creditPoolId;

    @Schema(description = "Grant consumed by this transaction, if applicable")
    private String creditGrantId;

    @Schema(description = "Transaction type: GRANT, DEDUCTION, EXPIRATION, REVERSAL, ADJUSTMENT")
    private String transactionType;

    @Schema(description = "Signed amount: positive for grants, negative for deductions")
    private BigDecimal amount;

    @Schema(description = "Pool balance before this transaction")
    private BigDecimal balanceBefore;

    @Schema(description = "Pool balance after this transaction")
    private BigDecimal balanceAfter;

    @Schema(description = "Human-readable description")
    private String description;

    @Schema(description = "ID of the reversed transaction, if this is a reversal")
    private String reversedTransactionId;

    @Schema(description = "Additional metadata")
    private Map<String, Object> metadata;

    @Schema(description = "Timestamp when the transaction was created")
    private Instant createdAt;
}
