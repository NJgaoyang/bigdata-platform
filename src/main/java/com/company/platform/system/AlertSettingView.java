package com.company.platform.system;

import java.time.LocalDateTime;

public record AlertSettingView(long id, String name, String channelType, String triggerEvent,
                               String webhookMasked, boolean secretConfigured, String keyword,
                               String customTemplate, boolean enabled,
                               LocalDateTime createdAt, LocalDateTime updatedAt) { }
