package com.company.platform.scheduler;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

@DisallowConcurrentExecution
public class WorkflowQuartzJob implements Job {
    @Autowired private LocalSchedulerGateway scheduler;
    @Override public void execute(JobExecutionContext context) throws JobExecutionException {
        String workflowCode = context.getMergedJobDataMap().getString("workflowCode");
        try { scheduler.runScheduled(workflowCode); }
        catch (RuntimeException ex) { throw new JobExecutionException(ex); }
    }
}
