package com.stokvault.notification;

/**
 * JNDI names of the two JMS queues, defined with @JMSDestinationDefinition on NotificationOutbox.
 */
public final class NotificationQueues {

    public static final String QUEUE = "java:app/jms/StokVaultNotifications";
    public static final String DEAD_LETTER_QUEUE = "java:app/jms/StokVaultNotificationsDLQ";

    /** Delivery attempts before a notification is moved to the dead-letter queue (SDD 4.4). */
    public static final int MAX_ATTEMPTS = 3;

    private NotificationQueues() {
    }

    /** Back-off before retry n (n = 2, 3...): 5 s, 10 s, 20 s... */
    public static long retryDelayMillis(int nextAttempt) {
        return 5_000L * (1L << (nextAttempt - 2));
    }
}
