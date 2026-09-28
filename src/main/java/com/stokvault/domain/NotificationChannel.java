package com.stokvault.domain;

public enum NotificationChannel {
    SMS,
    WHATSAPP,
    /** Fallback when SMS/WhatsApp can't be delivered (SDD 4.4). */
    EMAIL
}
