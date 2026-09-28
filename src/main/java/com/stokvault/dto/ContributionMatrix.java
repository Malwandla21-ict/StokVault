package com.stokvault.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The contributions grid: one row per member, one cell per recent cycle.
 *
 * Cell status: PAID (in full, on time), LATE (in full, after the due date), PARTIAL (some money
 * verified), AWAITING (payment reported, not yet verified), OUTSTANDING (nothing yet),
 * NONE (the member hadn't joined yet).
 */
public record ContributionMatrix(List<Column> cycles, List<Row> rows) {

    public record Column(UUID cycleId, int cycleNumber, LocalDate dueDate, String label) {
    }

    public record Row(UUID memberId, String name, int payoutPosition, List<String> cells, BigDecimal totalVerified) {
    }
}
