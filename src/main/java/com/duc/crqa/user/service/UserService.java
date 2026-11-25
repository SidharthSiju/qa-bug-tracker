package com.duc.crqa.user.service;

import com.duc.crqa.audit.service.AuditService;
import com.duc.crqa.common.enums.Role;
import com.duc.crqa.common.exception.ResourceNotFoundException;
import com.duc.crqa.user.dto.UserForm;
import com.duc.crqa.user.dto.UserSearchCriteria;
import com.duc.crqa.user.entity.User;
import com.duc.crqa.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<User> listAll() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<User> listActive() {
        return userRepository.findByActiveTrueOrderByDisplayNameAsc();
    }

    @Transactional(readOnly = true)
    public Page<User> search(UserSearchCriteria criteria, Pageable pageable) {
        return userRepository.findAll(buildSpecification(criteria), pageable);
    }

    private Specification<User> buildSpecification(UserSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.getSearch() != null && !criteria.getSearch().isBlank()) {
                String like = "%" + criteria.getSearch().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("displayName")), like),
                        cb.like(cb.lower(root.get("email")), like)));
            }
            if (criteria.getRole() != null) {
                predicates.add(cb.isMember(criteria.getRole(), root.get("roles")));
            }
            if (criteria.getActive() != null) {
                predicates.add(cb.equal(root.get("active"), criteria.getActive()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    @Transactional
    public User create(UserForm form) {
        User user = new User();
        user.setUsername(form.getUsername());
        user.setDisplayName(form.getDisplayName());
        user.setEmail(form.getEmail());
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        user.setActive(form.isActive());
        user.setEmailNotifications(form.isEmailNotifications());
        user.setRoles(form.getRoles() == null ? Set.of() : form.getRoles());

        User saved = userRepository.save(user);
        auditService.record("USER_CREATED", "USER", saved.getId(), null, saved.getUsername());
        return saved;
    }

    @Transactional
    public User update(Long id, UserForm form) {
        User user = getById(id);
        String oldValue = user.getUsername() + " / " + user.getDisplayName();
        user.setUsername(form.getUsername());
        user.setDisplayName(form.getDisplayName());
        user.setEmail(form.getEmail());
        user.setActive(form.isActive());
        user.setEmailNotifications(form.isEmailNotifications());
        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(form.getPassword()));
        }
        user.setRoles(form.getRoles() == null ? Set.of() : form.getRoles());

        User saved = userRepository.save(user);
        auditService.record("USER_UPDATED", "USER", saved.getId(), oldValue,
                saved.getUsername() + " / " + saved.getDisplayName());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<User> findDevelopers() {
        return listActive().stream()
                .filter(user -> user.hasRole(Role.DEVELOPER) || user.hasRole(Role.ADMIN))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<User> findQaUsers() {
        return listActive().stream()
                .filter(user -> user.hasRole(Role.QA_TESTER) || user.hasRole(Role.QA_LEAD)
                        || user.hasRole(Role.ADMIN))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<User> findSaUsers() {
        return listActive().stream()
                .filter(user -> user.hasRole(Role.SA_LEAD) || user.hasRole(Role.ADMIN))
                .toList();
    }
}
