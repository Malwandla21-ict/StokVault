package com.stokvault.web;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Formatting helpers for pages, e.g. #{fmt.rand(summary.balance)} or #{fmt.date(c.dueDate)}.
 */
@Named("fmt")
@ApplicationScoped
public class Format {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    // Changes with every deployment; appended to CSS URLs so browsers fetch the new version
    private final String assetVersion = Long.toString(System.currentTimeMillis(), 36);

    public String getAssetVersion() {
        return assetVersion;
    }

    public String rand(BigDecimal amount) {
        return Ui.rand(amount);
    }

    public String label(Object value) {
        return Ui.label(value);
    }

    public String date(LocalDate date) {
        return date == null ? "" : date.format(DATE);
    }

    public String dateTime(LocalDateTime dateTime) {
        return dateTime == null ? "" : dateTime.format(DATE_TIME);
    }

    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);

    /** "1 Sep" */
    public String shortDate(LocalDate date) {
        return date == null ? "" : date.format(SHORT);
    }

    public String shortDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "" : dateTime.format(SHORT);
    }

    /** "Wed 1 Oct 2026" */
    public String weekday(LocalDate date) {
        return date == null ? "" : date.format(WEEKDAY);
    }

    /** "Thandi Mokoena" -> "TM" (for avatars) */
    public String initials(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        StringBuilder out = new StringBuilder();
        for (String part : name.trim().split("\\s+")) {
            out.append(Character.toUpperCase(part.charAt(0)));
            if (out.length() == 2) {
                break;
            }
        }
        return out.toString();
    }

    /** "Good morning" / "Good afternoon" / "Good evening" */
    public String greeting() {
        int hour = LocalDateTime.now().getHour();
        return hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
    }

    /** Pill colour for any status value: good (done), warn (needs attention), bad, info or neutral. */
    public String tone(Object status) {
        if (status == null) {
            return "";
        }
        return switch (status.toString()) {
            case "PAID", "VERIFIED", "ACTIVE", "PASSED", "SENT", "RECEIVED", "CONFIRMED", "RECONCILED" -> "good";
            case "PENDING", "PENDING_REVIEW", "PENDING_APPROVAL", "AWAITING_VERIFICATION", "PARTIAL", "RETRYING",
                 "OVERRIDDEN", "SUSPENDED", "IN_PROGRESS", "QUEUED" -> "warn";
            case "REJECTED", "FAILED", "CANCELLED", "OUTSTANDING", "CLOSED" -> "bad";
            case "SCHEDULED", "DRAFT", "OPEN", "NEXT" -> "info";
            default -> "";
        };
    }

    /** First 12 characters of a hash, for display. */
    public String shortHash(String hash) {
        return hash == null || hash.length() < 12 ? hash : hash.substring(0, 12) + "...";
    }

    public boolean positive(BigDecimal amount) {
        return amount != null && amount.signum() > 0;
    }
}
