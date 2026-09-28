package com.stokvault.web;

import com.stokvault.domain.Money;
import com.stokvault.exception.Errors;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helpers shared by the Faces backing beans: run an action and show the outcome as a message.
 */
public final class Ui {

    private static final Logger LOG = Logger.getLogger(Ui.class.getName());
    private static final Locale SOUTH_AFRICA = Locale.of("en", "ZA");

    private Ui() {
    }

    /**
     * Runs an action (usually an EJB call). Success shows successMessage; a refused request
     * (business rule, permission, validation) shows the service's own explanation.
     */
    public static boolean attempt(Runnable action, String successMessage) {
        try {
            action.run();
            if (successMessage != null) {
                info(successMessage);
            }
            return true;
        } catch (RuntimeException e) {
            if (!Errors.isExpected(e)) {
                LOG.log(Level.SEVERE, "Unexpected error in a page action", e);
            }
            error(Errors.message(e));
            return false;
        }
    }

    public static void info(String message) {
        add(FacesMessage.SEVERITY_INFO, message);
    }

    public static void error(String message) {
        add(FacesMessage.SEVERITY_ERROR, message);
    }

    private static void add(FacesMessage.Severity severity, String message) {
        FacesContext context = FacesContext.getCurrentInstance();
        context.addMessage(null, new FacesMessage(severity, message, null));
        // Keep messages across a redirect (Post-Redirect-Get)
        context.getExternalContext().getFlash().setKeepMessages(true);
    }

    /**
     * "R 2 500,00": South African style (space between thousands, decimal comma), with a space
     * after the R as in the design. Whole rands drop the ",00" to keep big figures readable.
     */
    public static String rand(BigDecimal amount) {
        BigDecimal value = Money.orZero(amount);
        NumberFormat number = NumberFormat.getNumberInstance(SOUTH_AFRICA);
        boolean whole = value.stripTrailingZeros().scale() <= 0;
        number.setMinimumFractionDigits(whole ? 0 : 2);
        number.setMaximumFractionDigits(2);
        // en-ZA groups with a non-breaking space, so an amount never wraps across lines
        return "R " + number.format(value);
    }

    private static final java.util.Map<String, String> ACRONYMS = java.util.Map.of("EFT", "EFT", "SMS", "SMS", "WHATSAPP", "WhatsApp");

    /** "PENDING_REVIEW" -> "Pending review", "EFT" stays "EFT" */
    public static String label(Object value) {
        if (value == null) {
            return "";
        }
        String raw = value.toString();
        if (ACRONYMS.containsKey(raw)) {
            return ACRONYMS.get(raw);
        }
        String text = raw.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
