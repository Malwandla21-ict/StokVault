package com.stokvault.domain;

/**
 * Normalises South African mobile numbers so the same phone can't be registered twice in
 * different formats: "082 123 4567", "+27 82 123 4567" and "27821234567" all become "0821234567".
 */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    /** The normalised number, or null if it isn't a valid SA number. */
    public static String normalise(String input) {
        if (input == null) {
            return null;
        }
        String digits = input.replaceAll("[\\s()-]", "");
        if (digits.startsWith("+27")) {
            digits = "0" + digits.substring(3);
        } else if (digits.startsWith("27") && digits.length() == 11) {
            digits = "0" + digits.substring(2);
        }
        return digits.matches("0[1-8]\\d{8}") ? digits : null;
    }

    /** "0821234567" -> "082 123 4567" */
    public static String display(String normalised) {
        if (normalised == null || normalised.length() != 10) {
            return normalised;
        }
        return normalised.substring(0, 3) + " " + normalised.substring(3, 6) + " " + normalised.substring(6);
    }
}
