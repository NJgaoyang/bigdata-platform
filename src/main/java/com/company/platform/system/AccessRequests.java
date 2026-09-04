package com.company.platform.system;

import jakarta.validation.constraints.NotBlank;

public final class AccessRequests {
    private AccessRequests() { }
    public record UserRequest(@NotBlank String username, @NotBlank String displayName) { }
    public record RoleRequest(@NotBlank String roleCode, @NotBlank String roleName) { }
    public record PermissionRequest(@NotBlank String permissionCode) { }
    public record PermissionBindingRequest(long userId, @NotBlank String permissionCode) { }
    public record AlertChannelRequest(@NotBlank String name, @NotBlank String channelType,
                                      @NotBlank String configJson, boolean enabled) { }
}
