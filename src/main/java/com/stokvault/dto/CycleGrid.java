package com.stokvault.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The treasurer's "who has paid" grid for one cycle (SDD 6.1).
 * Row status: PAID (verified in full), PARTIAL (some verified), AWAITING_VERIFICATION (paid but
 * not yet verified), OUTSTANDING (nothing yet).
 */
public record CycleGrid(CycleView cycle, List<Row> rows) {

    public record Row(UUID memberId, String memberName, int payoutPosition, BigDecimal amountDue,
                      BigDecimal verified, BigDecimal awaitingVerification, String status) {
    }
}
