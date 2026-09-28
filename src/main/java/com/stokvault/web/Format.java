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

    /** First 12 characters of a hash, for display. */
    public String shortHash(String hash) {
        return hash == null || hash.length() < 12 ? hash : hash.substring(0, 12) + "...";
    }

    public boolean positive(BigDecimal amount) {
        return amount != null && amount.signum() > 0;
    }
}
