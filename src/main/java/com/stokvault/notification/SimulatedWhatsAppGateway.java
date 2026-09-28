package com.stokvault.notification;

import com.stokvault.domain.NotificationChannel;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.logging.Logger;

/**
 * Stand-in for the WhatsApp Business API during the pilot (logs instead of sending).
 * Simulate an outage with the system property stokvault.gateway.whatsapp.down=true.
 */
@ApplicationScoped
public class SimulatedWhatsAppGateway implements MessageGateway {

    private static final Logger LOG = Logger.getLogger(SimulatedWhatsAppGateway.class.getName());

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WHATSAPP;
    }

    @Override
    public void send(String recipient, String subject, String body) throws GatewayException {
        if (Boolean.getBoolean("stokvault.gateway.whatsapp.down")) {
            throw new GatewayException("WhatsApp gateway unreachable (simulated outage)");
        }
        LOG.info("[SIMULATED WHATSAPP to " + recipient + "] " + body);
    }
}
