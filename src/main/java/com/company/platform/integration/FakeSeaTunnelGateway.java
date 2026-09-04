package com.company.platform.integration;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FakeSeaTunnelGateway implements SeaTunnelGateway {
    private final Map<String, String> jobs = new ConcurrentHashMap<>();

    @Override public ValidationResult validate(String config) {
        return config == null || config.isBlank()
                ? new ValidationResult(false, "配置不能为空")
                : new ValidationResult(true, "Mock 校验通过，真实 SeaTunnel 延后到最终验收");
    }
    @Override public SubmitResult submit(String config) {
        String id = "mock-seatunnel-" + UUID.randomUUID();
        jobs.put(id, "SUCCESS");
        return new SubmitResult(id, "SUCCESS");
    }
    @Override public JobStatus status(String executionId) {
        return new JobStatus(executionId, jobs.getOrDefault(executionId, "NOT_FOUND"), "Mock 执行器");
    }
    @Override public void cancel(String executionId) { jobs.put(executionId, "CANCELED"); }
    @Override public String log(String executionId) { return "[Mock] SeaTunnel job " + executionId + " completed"; }
}
