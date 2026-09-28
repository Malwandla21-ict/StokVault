package com.stokvault.notification;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Observes NotificationRequest events and hands them to the outbox, which queues them on JMS.
 *
 * The observer is synchronous, so it runs inside the transaction that fired the event: the
 * notification is only really queued if that transaction commits (a rolled-back contribution
 * never produces a "contribution recorded" SMS).
 */
@ApplicationScoped
public class NotificationPublisher {

    @Inject
    private NotificationOutbox outbox;

    void onNotificationRequested(@Observes NotificationRequest request) {
        outbox.enqueue(request);
    }
}
