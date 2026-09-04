package com.company.platform.integration;

public interface SeaTunnelGateway {
    ValidationResult validate(String config);
    SubmitResult submit(String config);
    JobStatus status(String executionId);
    void cancel(String executionId);
    String log(String executionId);

    record ValidationResult(boolean valid, String message) { }
    record SubmitResult(String executionId, String status) { }
    record JobStatus(String executionId, String status, String message) { }
}
