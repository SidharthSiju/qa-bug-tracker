package com.duc.crqa.user.repository;

import com.duc.crqa.common.enums.Role;
import com.duc.crqa.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);

    List<User> findByActiveTrueOrderByDisplayNameAsc();

    List<User> findByRolesContainingAndActiveTrue(Role role);
}
