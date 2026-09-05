package com.company.platform.system;

import com.company.platform.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }

    @PostMapping("/login")
    public Result<AuthService.AuthSession> login(@Valid @RequestBody AuthRequests.LoginRequest request) {
        return Result.ok(service.login(request), "登录成功");
    }

    @PostMapping("/password")
    public Result<Void> changePassword(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @Valid @RequestBody AuthRequests.ChangePasswordRequest request) {
        service.changePassword(token(authorization), request);
        return Result.ok(null, "密码已修改，请使用新密码登录");
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        service.logout(token(authorization));
        return Result.ok(null, "已退出登录");
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String current = token(authorization);
        return Result.ok(Map.of(
                "username", service.currentUsername(current),
                "authenticated", service.authenticate(current),
                "permissions", service.permissionsForToken(current),
                "roleCode", service.roleForToken(current),
                "superAdmin", service.isSuperAdminToken(current)));
    }

    private String token(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : "";
    }
}
