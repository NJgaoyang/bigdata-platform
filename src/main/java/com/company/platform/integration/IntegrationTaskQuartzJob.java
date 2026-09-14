package com.company.platform.integration;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

@DisallowConcurrentExecution
public class IntegrationTaskQuartzJob implements Job {
    @Autowired private IntegrationService integrationService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        long taskId = context.getMergedJobDataMap().getLong("taskId");
        try { integrationService.execute(taskId, "SCHEDULED"); }
        catch (RuntimeException ex) { throw new JobExecutionException(ex); }
    }
}
