package com.stokvault.domain;

import java.time.DateTimeException;
import java.time.LocalDate;

/**
 * Validates South African ID numbers: 13 digits, YYMMDD date of birth, then a Luhn check digit.
 */
public final class SaIdNumber {

    private SaIdNumber() {
    }

    public static boolean isValid(String id) {
        if (id == null || !id.matches("\\d{13}")) {
            return false;
        }
        return hasValidBirthDate(id) && luhnCheckDigit(id.substring(0, 12)) == id.charAt(12) - '0';
    }

    /** The check digit that makes the first 12 digits a valid ID number (Luhn algorithm). */
    public static int luhnCheckDigit(String first12) {
        int sum = 0;
        for (int i = 0; i < first12.length(); i++) {
            int digit = first12.charAt(i) - '0';
            // Counting from the right of the full 13-digit number, every second digit is doubled
            if (i % 2 == 1) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
        }
        return (10 - sum % 10) % 10;
    }

    /** "8001015009087" -> "*********9087" (only the last four digits are ever shown). */
    public static String mask(String id) {
        if (id == null || id.length() < 4) {
            return "****";
        }
        return "*".repeat(id.length() - 4) + id.substring(id.length() - 4);
    }

    private static boolean hasValidBirthDate(String id) {
        int yy = Integer.parseInt(id.substring(0, 2));
        int mm = Integer.parseInt(id.substring(2, 4));
        int dd = Integer.parseInt(id.substring(4, 6));
        // Two-digit years: assume the most recent century that isn't in the future
        int currentYear = LocalDate.now().getYear();
        int year = 2000 + yy > currentYear ? 1900 + yy : 2000 + yy;
        try {
            LocalDate.of(year, mm, dd);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }
}
