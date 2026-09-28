package com.stokvault.domain;

public enum NotificationStatus {
    /** Written to the JMS queue; not yet picked up by the consumer. */
    QUEUED,
    SENT,
    /** Delivery failed; the message has been re-queued with a back-off delay. */
    RETRYING,
    /** All attempts failed; the message was moved to the dead-letter queue for manual review. */
    FAILED
}
