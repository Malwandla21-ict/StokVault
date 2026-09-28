package com.stokvault.report;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Renders a Statement as CSV (opens in Excel / Google Sheets).
 */
public final class StatementCsv {

    private StatementCsv() {
    }

    public static byte[] render(Statement statement) {
        StringBuilder csv = new StringBuilder();
        // A byte-order mark so Excel reads the file as UTF-8
        csv.append('﻿');
        csv.append(line(statement.headers()));
        statement.rows().forEach(row -> csv.append(line(row)));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String line(List<String> cells) {
        return cells.stream().map(StatementCsv::cell).collect(Collectors.joining(",")) + "\r\n";
    }

    static String cell(String value) {
        String v = value == null ? "" : value;
        // Spreadsheets run cells starting with = + - @ as formulas ("CSV injection"); neutralise
        // them unless they're plain numbers
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0 && !v.matches("-?\\d+(\\.\\d+)?")) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
