package com.company.platform.system;

import jakarta.validation.constraints.NotBlank;

public final class AuthRequests {
    private AuthRequests() { }
    public record LoginRequest(@NotBlank String username, @NotBlank String password) { }
}
