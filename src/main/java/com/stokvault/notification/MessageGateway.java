package com.stokvault.notification;

import com.stokvault.domain.NotificationChannel;

/**
 * A way of delivering a message to a person. Implementations are CDI beans, found by
 * NotificationDelivery through Instance&lt;MessageGateway&gt;.
 */
public interface MessageGateway {

    NotificationChannel channel();

    void send(String recipient, String subject, String body) throws GatewayException;
}
