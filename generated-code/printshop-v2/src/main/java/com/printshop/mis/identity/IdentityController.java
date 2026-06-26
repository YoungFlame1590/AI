package com.printshop.mis.identity;

import com.printshop.common.api.ApiResponse;
import com.printshop.mis.identity.IdentityService.AdminUserRequest;
import com.printshop.mis.identity.IdentityService.AdminUserUpdate;
import com.printshop.mis.identity.IdentityService.LoginRequest;
import com.printshop.mis.identity.IdentityService.RegisterRequest;
import com.printshop.mis.identity.IdentityService.ResetPasswordRequest;
import com.printshop.mis.identity.IdentityService.StoreRequest;
import com.printshop.mis.shared.ApiSupport;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IdentityController {

    private final IdentityService identityService;
    private final ApiSupport api;

    public IdentityController(IdentityService identityService, ApiSupport api) {
        this.identityService = identityService;
        this.api = api;
    }

    @PostMapping("/api/auth/login")
    public ApiResponse<?> login(@RequestBody LoginRequest request) {
        return api.ok(identityService.login(request));
    }

    @PostMapping("/api/auth/register")
    public ApiResponse<?> register(@RequestBody RegisterRequest request) {
        return api.ok(identityService.register(request));
    }

    @GetMapping("/api/me")
    public ApiResponse<?> me(Principal principal) {
        return api.ok(identityService.me(principal.getName()));
    }

    @GetMapping("/api/stores")
    public ApiResponse<?> stores() {
        return api.ok(identityService.stores());
    }

    @GetMapping("/api/users")
    public ApiResponse<?> users(Principal principal) {
        return api.ok(identityService.users(principal.getName()));
    }

    @GetMapping("/api/admin/users")
    public ApiResponse<?> adminUsers(Principal principal) {
        return api.ok(identityService.users(principal.getName()));
    }

    @PostMapping("/api/admin/users")
    public ApiResponse<?> createUser(Principal principal, @RequestBody AdminUserRequest request) {
        return api.ok(identityService.createUser(principal.getName(), request));
    }

    @PutMapping("/api/admin/users/{id}")
    public ApiResponse<?> updateUser(Principal principal, @PathVariable Long id, @RequestBody AdminUserUpdate request) {
        return api.ok(identityService.updateUser(principal.getName(), id, request));
    }

    @PostMapping("/api/admin/users/{id}/reset-password")
    public ApiResponse<?> resetPassword(Principal principal, @PathVariable Long id, @RequestBody ResetPasswordRequest request) {
        return api.ok(identityService.resetPassword(principal.getName(), id, request));
    }

    @GetMapping("/api/admin/stores")
    public ApiResponse<?> adminStores(Principal principal) {
        return api.ok(identityService.adminStores(principal.getName()));
    }

    @PostMapping("/api/admin/stores")
    public ApiResponse<?> createStore(Principal principal, @RequestBody StoreRequest request) {
        return api.ok(identityService.createStore(principal.getName(), request));
    }

    @PutMapping("/api/admin/stores/{id}")
    public ApiResponse<?> updateStore(Principal principal, @PathVariable Long id, @RequestBody StoreRequest request) {
        return api.ok(identityService.updateStore(principal.getName(), id, request));
    }
}
