package com.company.platform.integration;

public record IntegrationTableView(long id, long taskId, String sourceDatabase, String sourceTable,
                                   String targetDatabase, String targetTable, String partitionColumn) { }
