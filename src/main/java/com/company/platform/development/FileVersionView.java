package com.company.platform.development;

public record FileVersionView(long id, long fileId, int versionNo, String content, String checksum,
                              boolean publishFlag) { }
