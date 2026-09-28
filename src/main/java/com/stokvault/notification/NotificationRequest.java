package com.stokvault.notification;

import com.stokvault.domain.NotificationType;

import java.util.UUID;

/**
 * The CDI event a service fires when someone should be notified (SDD 4.4: "Jakarta CDI events
 * raise the notification intent").
 *
 * @param body         the full text to deliver
 * @param loggedBody   what to store in the notification log; differs from body only when the
 *                     message contains a secret (login codes), otherwise null
 * @param referenceKey optional key to avoid duplicates (e.g. one reminder per member per cycle per day)
 */
public record NotificationRequest(UUID memberId, UUID groupId, NotificationType type, String subject,
                                  String body, String loggedBody, String referenceKey) {

    public static NotificationRequest of(UUID memberId, UUID groupId, NotificationType type, String subject, String body) {
        return new NotificationRequest(memberId, groupId, type, subject, body, null, null);
    }
}
