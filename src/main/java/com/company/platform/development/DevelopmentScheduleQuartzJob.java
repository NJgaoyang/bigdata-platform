package com.company.platform.development;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

@DisallowConcurrentExecution
public class DevelopmentScheduleQuartzJob implements Job {
    @Autowired private DevelopmentScheduleService service;
    @Override public void execute(JobExecutionContext context) {
        long fileId=Long.parseLong(context.getMergedJobDataMap().getString("fileId"));
        service.executeScheduled(fileId);
    }
}
