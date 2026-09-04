package com.company.platform.lineage;

public record LineageView(long id, String sourceTable, String targetTable, String relationType,
                          Long fileId, Long fileVersionId) { }
