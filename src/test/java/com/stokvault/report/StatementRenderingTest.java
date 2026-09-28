package com.stokvault.report;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatementRenderingTest {

    private static Statement statement(int rows) {
        List<List<String>> data = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            data.add(List.of("2026-09-0" + (i % 9 + 1), "Member (" + i + ")", "500.00"));
        }
        return new Statement("Test statement", List.of("Period: all"), List.of("Date", "Member", "Amount"), data,
                List.of("Total: R" + rows * 500), List.of(2));
    }

    @Test
    void csvQuotesSpecialCharactersAndNeutralisesFormulas() {
        assertEquals("\"Smith, J\"", StatementCsv.cell("Smith, J"));
        assertEquals("\"say \"\"hi\"\"\"", StatementCsv.cell("say \"hi\""));
        assertEquals("'=HYPERLINK(\"x\")".replace("\"", "\"\""), StatementCsv.cell("=HYPERLINK(\"x\")").replaceAll("^\"|\"$", ""));
        assertEquals("-500.00", StatementCsv.cell("-500.00"));
    }

    @Test
    void csvHasAHeaderAndOneLinePerRow() {
        String csv = new String(StatementCsv.render(statement(3)), StandardCharsets.UTF_8);
        assertEquals(4, csv.strip().split("\r\n").length);
        assertTrue(csv.contains("Date,Member,Amount"));
    }

    @Test
    void pdfIsWellFormedWithCorrectCrossReferences() {
        byte[] pdf = StatementPdf.render(statement(5));
        String text = new String(pdf, StandardCharsets.ISO_8859_1);
        assertTrue(text.startsWith("%PDF-1.4"));
        assertTrue(text.strip().endsWith("%%EOF"));

        // Every xref entry must point at the start of "n 0 obj"
        int xrefStart = Integer.parseInt(text.substring(text.lastIndexOf("startxref") + 10, text.lastIndexOf("%%EOF")).trim());
        assertTrue(text.startsWith("xref", xrefStart));
        Matcher offsets = Pattern.compile("(\\d{10}) 00000 n ").matcher(text.substring(xrefStart));
        int object = 1;
        while (offsets.find()) {
            int offset = Integer.parseInt(offsets.group(1));
            assertTrue(text.startsWith(object + " 0 obj", offset), "object " + object);
            object++;
        }
        assertTrue(object > 6);
    }

    @Test
    void longStatementsRunOntoMorePages() {
        String text = new String(StatementPdf.render(statement(150)), StandardCharsets.ISO_8859_1);
        assertTrue(text.contains("/Count 3") || text.contains("/Count 2"), "expected more than one page");
        assertTrue(text.contains("Page 1 of"));
    }

    @Test
    void pdfEscapesBrackets() {
        assertEquals("Member \\(1\\) \\\\ x", StatementPdf.escape("Member (1) \\ x"));
    }
}
