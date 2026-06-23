package com.printshop.mis.identity;

import com.printshop.common.api.ApiResponse;
import com.printshop.mis.identity.IdentityService.LoginRequest;
import com.printshop.mis.shared.ApiSupport;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    @GetMapping("/api/me")
    public ApiResponse<?> me(Principal principal) {
        return api.ok(identityService.me(principal.getName()));
    }

    @GetMapping("/api/stores")
    public ApiResponse<?> stores() {
        return api.ok(identityService.stores());
    }

    @GetMapping("/api/users")
    public ApiResponse<?> users() {
        return api.ok(identityService.users());
    }
}
