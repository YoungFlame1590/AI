package com.printshop.mis.identity;

import com.printshop.common.exception.BusinessException;
import com.printshop.mis.domain.Store;
import com.printshop.mis.domain.UserAccount;
import com.printshop.mis.repository.StoreRepository;
import com.printshop.mis.repository.UserAccountRepository;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IdentityService {

    private final UserAccountRepository users;
    private final StoreRepository stores;

    public IdentityService(UserAccountRepository users, StoreRepository stores) {
        this.users = users;
        this.stores = stores;
    }

    public UserSession login(LoginRequest request) {
        UserAccount account = users.findByUsername(request.username())
                .filter(item -> item.password.equals(request.password()) && item.active)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误。"));
        return session(account, request.password());
    }

    public UserSession me(String username) {
        UserAccount account = requireUser(username);
        return session(account, account.password);
    }

    public List<Store> stores() {
        return stores.findAll();
    }

    public List<UserAccount> users(String username) {
        UserAccount current = requireUser(username);
        if (!"ADMIN".equals(current.role)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只有系统管理员可以查看用户列表。");
        }
        return users.findAll();
    }

    public UserAccount requireUser(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "登录状态无效。"));
    }

    public Map<String, Object> userView(UserAccount user) {
        Store store = user.storeId == null ? null : stores.findById(user.storeId).orElse(null);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.id);
        view.put("username", user.username);
        view.put("role", user.role);
        view.put("displayName", user.displayName);
        view.put("storeId", user.storeId);
        view.put("storeName", store == null ? "总部" : store.name);
        return view;
    }

    public String storeName(Long storeId) {
        if (storeId == null) {
            return "总部";
        }
        return stores.findById(storeId).map(store -> store.name).orElse("未知门店");
    }

    private UserSession session(UserAccount account, String password) {
        String raw = account.username + ":" + password;
        return new UserSession(
                userView(account),
                Base64.getEncoder().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8))
        );
    }

    public record LoginRequest(String username, String password) {
    }

    public record UserSession(Map<String, Object> user, String token) {
    }
}
