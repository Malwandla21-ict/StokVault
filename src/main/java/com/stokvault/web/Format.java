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
            case "PAID", "VERIFIED", "ACTIVE", "PASSED", "SENT", "RECEIVED", "RECONCILED" -> "good";
            case "CONFIRMED" -> "info";
            case "PENDING", "PENDING_REVIEW", "PENDING_APPROVAL", "AWAITING_VERIFICATION", "PARTIAL", "RETRYING",
                 "OVERRIDDEN", "SUSPENDED", "IN_PROGRESS", "QUEUED", "OUTSTANDING" -> "warn";
            case "REJECTED", "FAILED", "CANCELLED", "CLOSED" -> "bad";
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

    // ---- Plain-language wording ------------------------------------------------------------
    // The pages never show raw status codes to people: every status goes through one of these,
    // so the wording is the same on every screen and can be changed in one place.

    /** A payment's status, e.g. PENDING -> "Treasurer is checking it". */
    public String payment(Object verificationStatus) {
        if (verificationStatus == null) {
            return "";
        }
        return switch (verificationStatus.toString()) {
            case "PENDING" -> "Treasurer is checking it";
            case "PENDING_REVIEW" -> "Needs a second look";
            case "VERIFIED" -> "Paid ✓";
            case "REJECTED" -> "Not accepted";
            default -> label(verificationStatus);
        };
    }

    /** Where someone stands for the current round (the CycleGrid row status). */
    public String paidState(String rowStatus) {
        if (rowStatus == null) {
            return "";
        }
        return switch (rowStatus) {
            case "PAID" -> "Paid ✓";
            case "AWAITING_VERIFICATION" -> "Being checked";
            case "PARTIAL" -> "Part paid";
            case "OUTSTANDING" -> "Not paid yet";
            default -> label(rowStatus);
        };
    }

    /** A payout's status, e.g. PENDING_APPROVAL -> "Waiting for the committee". */
    public String payout(Object payoutStatus) {
        if (payoutStatus == null) {
            return "";
        }
        return switch (payoutStatus.toString()) {
            case "SCHEDULED" -> "Planned";
            case "PENDING_APPROVAL" -> "Waiting for the committee";
            case "CONFIRMED" -> "Ready to pay";
            case "PAID" -> "Paid out ✓";
            case "CANCELLED" -> "Cancelled";
            default -> label(payoutStatus);
        };
    }

    /** The automatic payout checks ("eligibility check" in the SDD), in plain words. */
    public String checks(Object eligibilityCheck) {
        if (eligibilityCheck == null) {
            return "";
        }
        return switch (eligibilityCheck.toString()) {
            case "NOT_RUN" -> "Checks running…";
            case "PASSED" -> "Checks passed ✓";
            case "FAILED" -> "Checks failed";
            case "OVERRIDDEN" -> "Allowed by the committee";
            default -> label(eligibilityCheck);
        };
    }

    /** "the cycle is 80% paid; ..." -> "The round is 80% paid; ..." (why a check failed). */
    public String reason(String notes) {
        if (notes == null || notes.isBlank()) {
            return "";
        }
        String text = notes.replace("the cycle", "the round").replace("contribution(s)", "payment(s)");
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** A round's status, for the treasurer. */
    public String roundState(Object cycleStatus) {
        if (cycleStatus == null) {
            return "";
        }
        return switch (cycleStatus.toString()) {
            case "OPEN" -> "Open for payments";
            case "CLOSED" -> "Closed";
            case "RECONCILED" -> "Closed and balanced ✓";
            default -> label(cycleStatus);
        };
    }

    /** A group's status. */
    public String groupState(Object groupStatus) {
        if (groupStatus == null) {
            return "";
        }
        return switch (groupStatus.toString()) {
            case "DRAFT" -> "Being set up";
            case "ACTIVE" -> "Running";
            case "SUSPENDED" -> "Paused";
            case "CLOSED" -> "Closed";
            default -> label(groupStatus);
        };
    }

    /** A message's delivery status (notification log). */
    public String message(Object notificationStatus) {
        if (notificationStatus == null) {
            return "";
        }
        return switch (notificationStatus.toString()) {
            case "QUEUED" -> "Waiting to send";
            case "SENT" -> "Sent ✓";
            case "RETRYING" -> "Trying again";
            case "FAILED" -> "Couldn't send";
            default -> label(notificationStatus);
        };
    }

    /** How someone paid. */
    public String method(Object paymentMethod) {
        if (paymentMethod == null) {
            return "";
        }
        return switch (paymentMethod.toString()) {
            case "EFT" -> "Bank transfer";
            case "CASH" -> "Cash";
            case "DEBIT_ORDER" -> "Debit order";
            default -> label(paymentMethod);
        };
    }

    /** The kind of stokvel, with what that means (for drop-downs). */
    public String groupType(Object type) {
        if (type == null) {
            return "";
        }
        return switch (type.toString()) {
            case "ROTATIONAL" -> "Rotational (members take turns to get the pot)";
            case "GROCERY" -> "Grocery (shared out for a bulk shop)";
            case "BURIAL" -> "Burial society (pays out for a funeral)";
            case "INVESTMENT" -> "Investment (shared by how much each put in)";
            default -> label(type);
        };
    }

    /** One sentence on how a stokvel pays out. */
    public String howItPaysOut(Object type, BigDecimal benefit) {
        if (type == null) {
            return "";
        }
        return switch (type.toString()) {
            case "ROTATIONAL" -> "Every round, one member gets the whole pot. Everyone gets a turn.";
            case "GROCERY" -> "The money is shared equally between members for the year-end bulk grocery shop.";
            case "BURIAL" -> "When there is a death in a member's family, the stokvel pays the family "
                    + rand(benefit) + " towards the funeral.";
            case "INVESTMENT" -> "The money is invested. When it is shared out, each member gets a share that matches how much they put in.";
            default -> "";
        };
    }

    /** 1 -> "1st", 2 -> "2nd", 3 -> "3rd", 11 -> "11th" */
    public String ordinal(Integer n) {
        if (n == null) {
            return "";
        }
        int lastTwo = n % 100;
        String suffix = lastTwo >= 11 && lastTwo <= 13 ? "th" : switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return n + suffix;
    }

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_SHORT = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_SHORT_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    /**
     * The name people use for a round instead of "Cycle 4": the month it is due for monthly
     * groups ("September"), "Week of 7 Sep" for weekly ones and "Jul–Sep" for quarterly ones.
     */
    public String round(LocalDate due, Object frequency) {
        if (due == null) {
            return "";
        }
        String f = frequency == null ? "MONTHLY" : frequency.toString();
        return switch (f) {
            case "WEEKLY" -> "Week of " + due.format(SHORT);
            case "QUARTERLY" -> due.minusMonths(2).format(MONTH_SHORT) + "–" + due.format(MONTH_SHORT);
            default -> due.getYear() == LocalDate.now().getYear() ? due.format(MONTH) : due.format(MONTH_YEAR);
        };
    }

    /** "Jan 2027": for roughly-when dates, like a payout turn months away. */
    public String monthYear(LocalDate date) {
        return date == null ? "" : date.format(MONTH_SHORT_YEAR);
    }

    /** Roughly when: "Thu 1 Oct" when it is close, "Jan 2027" when it is months away. */
    public String roughly(LocalDate date) {
        if (date == null) {
            return "";
        }
        long days = daysUntil(date);
        return days < 0 ? "soon" : days <= 45 ? day(date) : monthYear(date);
    }

    /** "around Thu 1 Oct", "around Jan 2027", or "soon" once the estimated date has passed. */
    public String around(LocalDate date) {
        String when = roughly(date);
        return when.isEmpty() || when.equals("soon") ? when : "around " + when;
    }

    /** "Fri 3 Oct" */
    public String day(LocalDate date) {
        return date == null ? "" : date.format(DAY);
    }

    /** Days from today until a date (negative once it has passed). */
    public long daysUntil(LocalDate date) {
        return date == null ? 0 : java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), date);
    }

    public boolean overdue(LocalDate due) {
        return due != null && due.isBefore(LocalDate.now());
    }

    /** "due today", "due tomorrow", "due Fri 3 Oct", "overdue by 3 days" */
    public String dueText(LocalDate due) {
        if (due == null) {
            return "";
        }
        long days = daysUntil(due);
        if (days == 0) {
            return "due today";
        }
        if (days == 1) {
            return "due tomorrow";
        }
        if (days < 0) {
            return "overdue by " + (-days) + (days == -1 ? " day" : " days");
        }
        return "due " + day(due);
    }

    /**
     * An audit entry's details for people: {"amount":500.00,"member":"Bongani"} becomes
     * "Amount: 500.00 · Member: Bongani". (The stored JSON, which the hash chain covers, is unchanged.)
     */
    public String details(String json) {
        if (json == null || json.isBlank() || !json.trim().startsWith("{")) {
            return json;
        }
        try (jakarta.json.JsonReader reader = jakarta.json.Json.createReader(new java.io.StringReader(json))) {
            StringBuilder out = new StringBuilder();
            reader.readObject().forEach((key, value) -> {
                if (out.length() > 0) {
                    out.append(" · ");
                }
                String words = key.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
                String text = value instanceof jakarta.json.JsonString s ? s.getString() : value.toString();
                out.append(cap(words)).append(": ").append(text);
            });
            return out.toString();
        } catch (RuntimeException e) {
            return json;
        }
    }

    /** An audit action in plain words, e.g. CYCLE_RECONCILED -> "Round balanced". */
    public String action(Object auditAction) {
        return label(auditAction)
                .replace("Contribution", "Payment")
                .replace("Cycle", "Round")
                .replace("reconciled", "balanced")
                .replace("eligibility checked", "checks run")
                .replace("eligibility overridden", "allowed by the committee")
                .replace("submitted for approval", "sent for a committee OK");
    }

    /** "overdue by 3 days" -> "Overdue by 3 days" */
    public String cap(String text) {
        return text == null || text.isEmpty() ? "" : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** "1 payment" / "3 payments" */
    public String count(long n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }

    /** a - b, never below zero (e.g. what someone still owes). */
    public BigDecimal minus(BigDecimal a, BigDecimal b) {
        BigDecimal x = a == null ? BigDecimal.ZERO : a;
        BigDecimal y = b == null ? BigDecimal.ZERO : b;
        return x.subtract(y).max(BigDecimal.ZERO);
    }
}
