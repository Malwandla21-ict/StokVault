package com.stokvault.notification;

import com.stokvault.entity.Member;
import com.stokvault.entity.NotificationEvent;
import com.stokvault.entity.StokvelGroup;
import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSDestinationDefinition;
import jakarta.jms.JMSDestinationDefinitions;
import jakarta.jms.MapMessage;
import jakarta.jms.Queue;
import jakarta.jms.JMSException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.logging.Logger;

/**
 * Writes notifications to the Jakarta Messaging (JMS) queue (SDD 4.4 / 8.2).
 *
 * The injected JMSContext is container-managed and joins the current JTA transaction, so the
 * message becomes visible to the consumer only when that transaction commits. The caller never
 * waits for an SMS gateway: sending to the queue is all it does.
 */
// Jakarta Messaging lets the application define its own queues with annotations, so no
// server configuration is needed. Payara's built-in broker creates them on deployment.
@JMSDestinationDefinitions({
        @JMSDestinationDefinition(name = NotificationQueues.QUEUE, interfaceName = "jakarta.jms.Queue",
                destinationName = "StokVaultNotifications"),
        @JMSDestinationDefinition(name = NotificationQueues.DEAD_LETTER_QUEUE, interfaceName = "jakarta.jms.Queue",
                destinationName = "StokVaultNotificationsDLQ")
})
@Stateless
public class NotificationOutbox {

    private static final Logger LOG = Logger.getLogger(NotificationOutbox.class.getName());

    @PersistenceContext(unitName = "StokVaultPU")
    private EntityManager em;

    @Inject
    private JMSContext jms;

    @Resource(lookup = NotificationQueues.QUEUE)
    private Queue queue;

    @Resource(lookup = NotificationQueues.DEAD_LETTER_QUEUE)
    private Queue deadLetterQueue;

    public void enqueue(NotificationRequest request) {
        Member member = em.find(Member.class, request.memberId());
        if (member == null) {
            return;
        }
        if (request.referenceKey() != null && alreadyQueued(request.referenceKey())) {
            return; // e.g. today's reminder for this cycle was already sent
        }
        NotificationEvent event = new NotificationEvent();
        event.setMember(member);
        event.setGroup(request.groupId() == null ? null : em.find(StokvelGroup.class, request.groupId()));
        event.setType(request.type());
        event.setChannel(member.getPreferredChannel());
        event.setSubject(request.subject());
        event.setBody(request.loggedBody() != null ? request.loggedBody() : request.body());
        event.setReferenceKey(request.referenceKey());
        em.persist(event);
        send(queue, event, request.body(), 1, 0);
    }

    /** Puts a failed notification back on the queue, delivered after a back-off delay. */
    public void requeue(NotificationEvent event, String body, int nextAttempt) {
        send(queue, event, body, nextAttempt, NotificationQueues.retryDelayMillis(nextAttempt));
    }

    /** Gives up: the message goes to the dead-letter queue for manual review. */
    public void deadLetter(NotificationEvent event, String body, int attempts) {
        send(deadLetterQueue, event, body, attempts, 0);
        LOG.warning("Notification " + event.getId() + " moved to the dead-letter queue after "
                + attempts + " attempts: " + event.getLastError());
    }

    private void send(Queue destination, NotificationEvent event, String body, int attempt, long delayMillis) {
        try {
            MapMessage message = jms.createMapMessage();
            message.setString("notificationId", event.getId().toString());
            message.setString("body", body);
            message.setInt("attempt", attempt);
            jms.createProducer().setDeliveryDelay(delayMillis).send(destination, message);
        } catch (JMSException e) {
            // Rolls back the caller's transaction too: better to fail loudly than lose a message
            throw new IllegalStateException("Could not queue notification " + event.getId(), e);
        }
    }

    private boolean alreadyQueued(String referenceKey) {
        return em.createQuery("SELECT COUNT(n) FROM NotificationEvent n WHERE n.referenceKey = :key", Long.class)
                .setParameter("key", referenceKey)
                .getSingleResult() > 0;
    }
}
