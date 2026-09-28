package com.stokvault.notification;

import com.stokvault.domain.NotificationChannel;
import com.stokvault.domain.NotificationStatus;
import com.stokvault.entity.Member;
import com.stokvault.entity.NotificationEvent;
import jakarta.ejb.Stateless;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Delivers one queued notification (SDD 4.4):
 *  1. try the member's preferred channel (SMS or WhatsApp),
 *  2. if that gateway is unreachable, fall back to email (when the member has an address),
 *  3. if everything failed, re-queue with a growing delay, up to 3 attempts,
 *  4. then move the message to the dead-letter queue and mark it FAILED for manual review.
 */
@Stateless
public class NotificationDelivery {

    @PersistenceContext(unitName = "StokVaultPU")
    private EntityManager em;

    @Inject
    private NotificationOutbox outbox;

    // Every MessageGateway bean in the application (SMS, WhatsApp, email)
    @Inject
    @Any
    private Instance<MessageGateway> gateways;

    public void deliver(UUID notificationId, String body, int attempt) {
        NotificationEvent event = em.find(NotificationEvent.class, notificationId);
        if (event == null || event.getStatus() == NotificationStatus.SENT) {
            return; // already handled (e.g. a duplicate delivery from the broker)
        }
        event.setAttempts(attempt);
        Member member = event.getMember();

        List<NotificationChannel> channels = new ArrayList<>(List.of(event.getChannel()));
        if (event.getChannel() != NotificationChannel.EMAIL && member.getEmail() != null) {
            channels.add(NotificationChannel.EMAIL);
        }

        List<String> errors = new ArrayList<>();
        for (NotificationChannel channel : channels) {
            try {
                gateway(channel).send(recipient(member, channel), event.getSubject(), body);
                event.setStatus(NotificationStatus.SENT);
                event.setDeliveredChannel(channel);
                event.setSentAt(LocalDateTime.now());
                event.setLastError(errors.isEmpty() ? null : String.join("; ", errors));
                return;
            } catch (GatewayException e) {
                errors.add(channel + ": " + e.getMessage());
            }
        }

        event.setLastError(truncate(String.join("; ", errors)));
        if (attempt < NotificationQueues.MAX_ATTEMPTS) {
            event.setStatus(NotificationStatus.RETRYING);
            outbox.requeue(event, body, attempt + 1);
        } else {
            event.setStatus(NotificationStatus.FAILED);
            outbox.deadLetter(event, body, attempt);
        }
    }

    private MessageGateway gateway(NotificationChannel channel) {
        for (MessageGateway gateway : gateways) {
            if (gateway.channel() == channel) {
                return gateway;
            }
        }
        throw new IllegalStateException("No gateway for " + channel);
    }

    private static String recipient(Member member, NotificationChannel channel) {
        return channel == NotificationChannel.EMAIL ? member.getEmail() : member.getPhoneNumber();
    }

    private static String truncate(String text) {
        return text.length() <= 500 ? text : text.substring(0, 497) + "...";
    }
}
