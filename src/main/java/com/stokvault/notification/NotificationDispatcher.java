package com.stokvault.notification;

import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The queue consumer (SDD 4.4): a Message-Driven Bean. The container calls onMessage for each
 * message on the notification queue, in the background, completely separate from the request
 * that queued it.
 */
// @MessageDriven: an EJB that listens to a JMS destination instead of being called directly.
// destinationLookup names the queue; the container manages the connection and a pool of instances.
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = NotificationQueues.QUEUE),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Queue")
})
public class NotificationDispatcher implements MessageListener {

    private static final Logger LOG = Logger.getLogger(NotificationDispatcher.class.getName());

    @Inject
    private NotificationDelivery delivery;

    @Override
    public void onMessage(Message message) {
        try {
            MapMessage map = (MapMessage) message;
            delivery.deliver(UUID.fromString(map.getString("notificationId")), map.getString("body"), map.getInt("attempt"));
        } catch (JMSException | ClassCastException | IllegalArgumentException e) {
            // A malformed message can never succeed; log it rather than let the broker redeliver it forever
            LOG.log(Level.SEVERE, "Discarding unreadable notification message", e);
        }
    }
}
