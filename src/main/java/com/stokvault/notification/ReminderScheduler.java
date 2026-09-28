package com.stokvault.notification;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Background reminder scheduling with Jakarta Concurrency (SDD 3.1 cross-cutting tier).
 *
 * A ManagedScheduledExecutorService is the container-safe version of Java's scheduled executor:
 * its threads are managed by Payara, so tasks can use EJBs, transactions and JPA.
 * Reminders go out every day at 08:00.
 */
@Singleton
@Startup
public class ReminderScheduler {

    private static final Logger LOG = Logger.getLogger(ReminderScheduler.class.getName());
    private static final LocalTime SEND_AT = LocalTime.of(8, 0);

    // The default managed scheduled executor every Jakarta EE server provides
    @Resource
    private ManagedScheduledExecutorService executor;

    @Inject
    private ReminderService reminders;

    private ScheduledFuture<?> task;

    @PostConstruct
    void start() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime firstRun = now.toLocalDate().atTime(SEND_AT);
        if (!firstRun.isAfter(now)) {
            firstRun = LocalDate.now().plusDays(1).atTime(SEND_AT);
        }
        long initialDelay = Duration.between(now, firstRun).toMinutes();
        task = executor.scheduleAtFixedRate(this::run, initialDelay, TimeUnit.DAYS.toMinutes(1), TimeUnit.MINUTES);
    }

    @PreDestroy
    void stop() {
        if (task != null) {
            task.cancel(false);
        }
    }

    private void run() {
        try {
            int sent = reminders.sendDueReminders();
            LOG.info("Queued " + sent + " contribution reminder(s)");
        } catch (RuntimeException e) {
            // Never let one failure cancel the repeating task
            LOG.log(Level.SEVERE, "Reminder run failed", e);
        }
    }
}
