package com.stokvault.notification;

import com.stokvault.domain.NotificationChannel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;
import java.util.logging.Logger;

/**
 * Email notifications with Jakarta Mail (SDD 3.3), used as the fallback channel.
 *
 * Sends real email when an SMTP server is configured through system properties, e.g.
 *   asadmin create-system-properties stokvault.mail.host=smtp.example.com:stokvault.mail.port=25
 * (optionally stokvault.mail.from). Without stokvault.mail.host it only logs, like the SMS gateway.
 * Simulate an outage with stokvault.gateway.email.down=true.
 */
@ApplicationScoped
public class EmailGateway implements MessageGateway {

    private static final Logger LOG = Logger.getLogger(EmailGateway.class.getName());

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(String recipient, String subject, String body) throws GatewayException {
        if (Boolean.getBoolean("stokvault.gateway.email.down")) {
            throw new GatewayException("Mail server unreachable (simulated outage)");
        }
        String host = System.getProperty("stokvault.mail.host");
        if (host == null || host.isBlank()) {
            LOG.info("[SIMULATED EMAIL to " + recipient + "] " + subject + " - " + body);
            return;
        }
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", System.getProperty("stokvault.mail.port", "25"));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        try {
            MimeMessage message = new MimeMessage(Session.getInstance(props));
            message.setFrom(new InternetAddress(System.getProperty("stokvault.mail.from", "no-reply@stokvault.local")));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
            message.setSubject(subject == null ? "StokVault" : subject);
            message.setText(body);
            Transport.send(message);
        } catch (MessagingException e) {
            throw new GatewayException("Email to " + recipient + " failed: " + e.getMessage(), e);
        }
    }
}
