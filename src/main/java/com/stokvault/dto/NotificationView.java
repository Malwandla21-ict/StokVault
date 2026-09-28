package com.stokvault.dto;

import com.stokvault.domain.NotificationChannel;
import com.stokvault.domain.NotificationStatus;
import com.stokvault.domain.NotificationType;
import com.stokvault.entity.NotificationEvent;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationView(UUID id, String memberName, String groupName, NotificationType type,
                               NotificationChannel channel, NotificationChannel deliveredChannel,
                               NotificationStatus status, int attempts, String body, String lastError,
                               LocalDateTime createdAt, LocalDateTime sentAt) {

    public static NotificationView from(NotificationEvent n) {
        return new NotificationView(n.getId(), n.getMember().getFullName(),
                n.getGroup() == null ? null : n.getGroup().getName(), n.getType(), n.getChannel(),
                n.getDeliveredChannel(), n.getStatus(), n.getAttempts(), n.getBody(), n.getLastError(),
                n.getCreatedAt(), n.getSentAt());
    }
}
