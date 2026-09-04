package com.company.platform.system;

public record AlertChannelView(long id, String name, String channelType, String configJson, boolean enabled) { }
