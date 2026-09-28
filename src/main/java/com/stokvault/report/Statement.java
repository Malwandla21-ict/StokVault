package com.stokvault.report;

import java.util.List;

/**
 * A report in a neutral form, rendered to CSV (StatementCsv) or PDF (StatementPdf).
 *
 * @param numericColumns indexes of columns holding amounts (right-aligned in the PDF)
 */
public record Statement(String title, List<String> subtitle, List<String> headers, List<List<String>> rows,
                        List<String> footer, List<Integer> numericColumns) {
}
