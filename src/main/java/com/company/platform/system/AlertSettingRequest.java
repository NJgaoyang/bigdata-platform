package com.company.platform.system;

import jakarta.validation.constraints.NotBlank;

public record AlertSettingRequest(@NotBlank String name,
                                  @NotBlank String channelType,
                                  String triggerEvent,
                                  String webhook,
                                  String secret,
                                  String keyword,
                                  String customTemplate,
                                  boolean enabled) { }
