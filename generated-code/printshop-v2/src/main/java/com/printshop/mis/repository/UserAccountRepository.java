package com.printshop.mis.repository;

import com.printshop.mis.domain.UserAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByUsername(String username);
    Optional<UserAccount> findByDisplayNameIgnoreCase(String displayName);
    boolean existsByUsername(String username);
    long countByRoleAndActiveTrue(String role);
}
