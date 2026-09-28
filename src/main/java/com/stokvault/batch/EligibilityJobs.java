package com.stokvault.batch;

import jakarta.annotation.security.PermitAll;
import jakarta.batch.operations.JobOperator;
import jakarta.batch.runtime.BatchRuntime;
import jakarta.batch.runtime.JobExecution;
import jakarta.ejb.Stateless;

import java.util.Date;
import java.util.Properties;
import java.util.UUID;

/**
 * Starts the "payout-eligibility" Jakarta Batch job (META-INF/batch-jobs/payout-eligibility.xml)
 * and reports on its runs. Callers check permissions first (see JobResource, PayoutResource).
 */
@Stateless
@PermitAll
public class EligibilityJobs {

    public static final String JOB_NAME = "payout-eligibility";

    public record Status(long executionId, String status, Date started, Date ended) {
    }

    /** Starts a run for one group, or every group when groupId is null. Returns immediately. */
    public long start(UUID groupId) {
        Properties parameters = new Properties();
        parameters.setProperty("groupId", groupId == null ? "" : groupId.toString());
        return operator().start(JOB_NAME, parameters);
    }

    public Status status(long executionId) {
        JobExecution execution = operator().getJobExecution(executionId);
        return new Status(executionId, execution.getBatchStatus().name(), execution.getStartTime(), execution.getEndTime());
    }

    private static JobOperator operator() {
        return BatchRuntime.getJobOperator();
    }
}
