package com.company.platform.system;

import jakarta.validation.constraints.NotBlank;

public final class AccessRequests {
    private AccessRequests() { }
    public record UserRequest(@NotBlank String username, @NotBlank String displayName, String password,
                              String roleCode, String status) {
        public UserRequest(String username, String displayName) { this(username, displayName, null, "USER", "ACTIVE"); }
        public UserRequest(String username, String displayName, String password) { this(username, displayName, password, "USER", "ACTIVE"); }
    }
    public record UserUpdateRequest(@NotBlank String username, @NotBlank String displayName, String password,
                                    String roleCode, @NotBlank String status) {
        public UserUpdateRequest(String username, String displayName, String password, String status) {
            this(username, displayName, password, "USER", status);
        }
    }
    public record RoleRequest(@NotBlank String roleCode, @NotBlank String roleName) { }
    public record PermissionRequest(@NotBlank String permissionCode) { }
    public record PermissionBindingRequest(long userId, @NotBlank String permissionCode) { }
    public record AlertChannelRequest(@NotBlank String name, @NotBlank String channelType,
                                      @NotBlank String configJson, boolean enabled) { }
}
