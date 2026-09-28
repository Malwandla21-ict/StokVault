package com.stokvault.dto;

import com.stokvault.domain.GroupStatus;
import com.stokvault.domain.GroupType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** The Coop Office's platform-wide view (SDD use case "View Platform-Wide Reports"). */
public record PlatformReport(int groups, long activeMembers, BigDecimal totalBalance, BigDecimal totalArrears,
                             long awaitingVerification, long payoutsAwaitingApproval, List<Row> rows) {

    public record Row(UUID groupId, String name, GroupType type, GroupStatus status, long activeMembers,
                      BigDecimal balance, BigDecimal totalVerified, BigDecimal totalPaidOut, BigDecimal arrears,
                      long awaitingVerification, boolean auditChainValid) {
    }
}
