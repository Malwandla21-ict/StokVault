package com.stokvault.resource;

import com.stokvault.report.Statement;
import com.stokvault.report.StatementCsv;
import com.stokvault.report.StatementPdf;
import com.stokvault.service.StatementService;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Exportable reports (SDD 4.5): /api/groups/{groupId}/reports/...?format=pdf|csv
 * The response is a file download (Content-Disposition: attachment).
 */
@Path("/groups/{groupId}/reports")
public class ReportResource {

    @Inject
    private StatementService statements;

    // GET .../reports/statement?format=pdf&from=2026-06-01&to=2026-09-30
    @GET
    @Path("/statement")
    public Response groupStatement(@PathParam("groupId") UUID groupId, @QueryParam("format") @DefaultValue("pdf") String format,
                                   @QueryParam("from") LocalDate from, @QueryParam("to") LocalDate to) {
        return file(statements.groupStatement(groupId, from, to), "group-statement", format);
    }

    @GET
    @Path("/members/{memberId}/statement")
    public Response memberStatement(@PathParam("groupId") UUID groupId, @PathParam("memberId") UUID memberId,
                                    @QueryParam("format") @DefaultValue("pdf") String format,
                                    @QueryParam("from") LocalDate from, @QueryParam("to") LocalDate to) {
        return file(statements.memberStatement(groupId, memberId, from, to), "member-statement", format);
    }

    @GET
    @Path("/audit")
    public Response audit(@PathParam("groupId") UUID groupId, @QueryParam("format") @DefaultValue("pdf") String format) {
        return file(statements.auditReport(groupId), "audit-trail", format);
    }

    private static Response file(Statement statement, String name, String format) {
        boolean csv = "csv".equalsIgnoreCase(format);
        byte[] body = csv ? StatementCsv.render(statement) : StatementPdf.render(statement);
        String filename = "stokvault-" + name + "-" + LocalDate.now() + (csv ? ".csv" : ".pdf");
        return Response.ok(body, csv ? "text/csv; charset=UTF-8" : "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .build();
    }
}
