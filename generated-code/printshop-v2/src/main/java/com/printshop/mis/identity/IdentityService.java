package com.printshop.mis.identity;

import static com.printshop.mis.shared.MisSupport.notFound;
import static com.printshop.mis.shared.MisSupport.text;

import com.printshop.common.exception.BusinessException;
import com.printshop.mis.audit.AuditTrailService;
import com.printshop.mis.domain.Store;
import com.printshop.mis.domain.UserAccount;
import com.printshop.mis.repository.StoreRepository;
import com.printshop.mis.repository.UserAccountRepository;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IdentityService {

    private final UserAccountRepository users;
    private final StoreRepository stores;
    private final PasswordEncoder passwordEncoder;
    private final AuditTrailService audit;

    public IdentityService(UserAccountRepository users, StoreRepository stores, PasswordEncoder passwordEncoder, AuditTrailService audit) {
        this.users = users;
        this.stores = stores;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    public UserSession login(LoginRequest request) {
        UserAccount account = users.findByUsername(request.username())
                .filter(item -> passwordEncoder.matches(request.password(), item.password) && item.active)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误。"));
        return session(account, request.password());
    }

    public UserSession me(String username) {
        UserAccount account = requireUser(username);
        return new UserSession(userView(account), null);
    }

    public List<Store> stores() {
        return stores.findByActiveTrueOrderByNameAsc();
    }

    public List<UserAccount> users(String username) {
        requireAdmin(username);
        return users.findAll();
    }

    public List<Store> adminStores(String username) {
        requireAdmin(username);
        return stores.findAll();
    }

    @Transactional
    public UserSession register(RegisterRequest request) {
        validateUsername(request.username());
        validatePassword(request.password());
        if (users.existsByUsername(request.username())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "用户名已存在。");
        }
        String displayName = validateDisplayName(request.displayName());
        UserAccount account = new UserAccount();
        account.username = request.username().trim();
        account.password = passwordEncoder.encode(request.password());
        account.role = "CUSTOMER";
        account.displayName = displayName;
        account.storeId = requireActiveStoreId(request.storeId(), true);
        account.active = true;
        UserAccount saved = users.save(account);
        audit.record(saved.username, "AUTH", "REGISTER_CUSTOMER", "USER", saved.id, saved.username);
        return session(saved, request.password());
    }

    @Transactional
    public UserAccount createUser(String username, AdminUserRequest request) {
        requireAdmin(username);
        validateUsername(request.username());
        validatePassword(request.password());
        if (users.existsByUsername(request.username())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "用户名已存在。");
        }
        String displayName = validateDisplayName(request.displayName());
        UserAccount account = new UserAccount();
        account.username = request.username().trim();
        account.password = passwordEncoder.encode(request.password());
        account.role = normalizeRole(request.role());
        account.displayName = displayName;
        account.storeId = requireStoreForRole(account.role, request.storeId());
        account.active = request.active() == null || request.active();
        UserAccount saved = users.save(account);
        audit.record(username, "AUTH", "CREATE_USER", "USER", saved.id, saved.username);
        return saved;
    }

    @Transactional
    public UserAccount updateUser(String username, Long id, AdminUserUpdate request) {
        UserAccount current = requireAdmin(username);
        UserAccount account = users.findById(id).orElseThrow(() -> notFound("用户", id));
        String nextRole = request.role() == null ? account.role : normalizeRole(request.role());
        boolean nextActive = request.active() == null ? account.active : request.active();
        if (current.id.equals(account.id) && (!nextActive || !"ADMIN".equals(nextRole))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "不能停用当前登录账号或移除其管理员角色。");
        }
        if (account.active && "ADMIN".equals(account.role)
                && (!nextActive || !"ADMIN".equals(nextRole))
                && users.countByRoleAndActiveTrue("ADMIN") <= 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "系统必须至少保留一个启用的管理员账号。");
        }
        if (request.role() != null) {
            account.role = nextRole;
        }
        if (request.displayName() != null) {
            account.displayName = validateDisplayName(request.displayName());
        }
        if (request.storeId() != null || Set.of("CUSTOMER", "CLERK", "MANAGER").contains(account.role)) {
            account.storeId = requireStoreForRole(account.role, request.storeId() == null ? account.storeId : request.storeId());
        }
        if (request.active() != null) {
            account.active = nextActive;
        }
        audit.record(username, "AUTH", "UPDATE_USER", "USER", account.id, account.username);
        return users.save(account);
    }

    @Transactional
    public UserAccount resetPassword(String username, Long id, ResetPasswordRequest request) {
        requireAdmin(username);
        validatePassword(request.password());
        UserAccount account = users.findById(id).orElseThrow(() -> notFound("用户", id));
        account.password = passwordEncoder.encode(request.password());
        audit.record(username, "AUTH", "RESET_PASSWORD", "USER", account.id, account.username);
        return users.save(account);
    }

    @Transactional
    public UserAccount deleteUser(String username, Long id) {
        UserAccount current = requireAdmin(username);
        UserAccount account = users.findById(id).orElseThrow(() -> notFound("用户", id));
        if (current.id.equals(account.id)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "不能停用当前登录账号。");
        }
        if (account.active && "ADMIN".equals(account.role) && users.countByRoleAndActiveTrue("ADMIN") <= 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "系统必须至少保留一个启用的管理员账号。");
        }
        account.active = false;
        audit.record(username, "AUTH", "DEACTIVATE_USER", "USER", account.id, account.username);
        return users.save(account);
    }

    @Transactional
    public Store createStore(String username, StoreRequest request) {
        requireAdmin(username);
        validateStoreCode(request.code(), null);
        Store store = new Store();
        applyStore(store, request);
        Store saved = stores.save(store);
        audit.record(username, "AUTH", "CREATE_STORE", "STORE", saved.id, saved.code);
        return saved;
    }

    @Transactional
    public Store updateStore(String username, Long id, StoreRequest request) {
        requireAdmin(username);
        Store store = stores.findById(id).orElseThrow(() -> notFound("门店", id));
        validateStoreCode(request.code(), id);
        applyStore(store, request);
        audit.record(username, "AUTH", "UPDATE_STORE", "STORE", store.id, store.code);
        return stores.save(store);
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

    public String resolveActiveCourierUsername(String accountOrDisplayName) {
        if (accountOrDisplayName == null || accountOrDisplayName.isBlank() || "待分配".equals(accountOrDisplayName)) {
            return null;
        }
        String normalized = accountOrDisplayName.trim();
        UserAccount byUsername = users.findByUsername(normalized).orElse(null);
        if (byUsername != null && byUsername.active && "COURIER".equals(byUsername.role)) {
            return byUsername.username;
        }
        List<UserAccount> matches = users.findAllByDisplayNameIgnoreCase(normalized).stream()
                .filter(account -> account.active && "COURIER".equals(account.role))
                .toList();
        if (matches.size() > 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "配送员显示名称重复，请使用登录账号分配配送任务。");
        }
        return matches.isEmpty() ? null : matches.get(0).username;
    }

    private UserAccount requireAdmin(String username) {
        UserAccount current = requireUser(username);
        if (!"ADMIN".equals(current.role)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "只有系统管理员可以执行该操作。");
        }
        return current;
    }

    private Long requireActiveStoreId(Long storeId, boolean allowNullDefault) {
        Long resolved = storeId;
        if (resolved == null && allowNullDefault) {
            resolved = stores.findByActiveTrueOrderByNameAsc().stream().findFirst().map(store -> store.id).orElse(null);
        }
        if (resolved == null) {
            return null;
        }
        Long resolvedId = resolved;
        Store store = stores.findById(resolvedId).orElseThrow(() -> notFound("门店", resolvedId));
        if (!store.active) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "门店已停用，不能分配账号。");
        }
        return store.id;
    }

    private Long requireStoreForRole(String role, Long storeId) {
        if (Set.of("CUSTOMER", "CLERK", "MANAGER").contains(role)) {
            Long resolved = requireActiveStoreId(storeId, true);
            if (resolved == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "该角色必须分配门店。");
            }
            return resolved;
        }
        return storeId == null ? null : requireActiveStoreId(storeId, false);
    }

    private String normalizeRole(String role) {
        String normalized = text(role, "CUSTOMER").trim().toUpperCase();
        if (!Set.of("CUSTOMER", "CLERK", "MANAGER", "OPS", "FINANCE", "COURIER", "ADMIN").contains(normalized)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "不支持的角色：" + role);
        }
        return normalized;
    }

    private void validateUsername(String username) {
        if (username == null || !username.matches("[A-Za-z0-9_]{3,32}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "用户名需为 3-32 位字母、数字或下划线。");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 6 || password.length() > 64) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "密码需为 6-64 位。");
        }
    }

    private String validateDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "显示名称不能为空。");
        }
        String normalized = displayName.trim();
        if (normalized.length() > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "显示名称不能超过 100 个字符。");
        }
        return normalized;
    }

    private void validateStoreCode(String code, Long currentId) {
        if (code == null || !code.matches("[A-Za-z0-9-]{2,32}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "门店编码需为 2-32 位字母、数字或短横线。");
        }
        stores.findByCode(code.trim()).ifPresent(existing -> {
            if (!existing.id.equals(currentId)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "门店编码已存在。");
            }
        });
    }

    private void applyStore(Store store, StoreRequest request) {
        store.code = request.code().trim();
        store.name = text(request.name(), store.name == null ? store.code : store.name);
        store.address = text(request.address(), store.address);
        store.phone = text(request.phone(), store.phone);
        store.active = request.active() == null || request.active();
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

    public record RegisterRequest(String username, String password, String displayName, Long storeId) {
    }

    public record AdminUserRequest(String username, String password, String role, String displayName, Long storeId, Boolean active) {
    }

    public record AdminUserUpdate(String role, String displayName, Long storeId, Boolean active) {
    }

    public record ResetPasswordRequest(String password) {
    }

    public record StoreRequest(String code, String name, String address, String phone, Boolean active) {
    }

    public record UserSession(Map<String, Object> user, String token) {
    }
}
