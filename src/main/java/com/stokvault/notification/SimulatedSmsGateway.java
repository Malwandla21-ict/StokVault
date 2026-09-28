package com.stokvault.notification;

import com.stokvault.domain.NotificationChannel;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.logging.Logger;

/**
 * Stand-in for the SMS gateway during the pilot: "sends" by writing to the server log
 * (glassfish/domains/domain1/logs/server.log), which is also where login codes can be read
 * while testing. Replace with a real provider's HTTP API (called from here) for production.
 *
 * To test the retry, fallback and dead-letter behaviour, simulate an outage:
 *   asadmin create-system-properties stokvault.gateway.sms.down=true
 * and remove it again with delete-system-property.
 */
@ApplicationScoped
public class SimulatedSmsGateway implements MessageGateway {

    private static final Logger LOG = Logger.getLogger(SimulatedSmsGateway.class.getName());

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.SMS;
    }

    @Override
    public void send(String recipient, String subject, String body) throws GatewayException {
        if (Boolean.getBoolean("stokvault.gateway.sms.down")) {
            throw new GatewayException("SMS gateway unreachable (simulated outage)");
        }
        LOG.info("[SIMULATED SMS to " + recipient + "] " + body);
    }
}
