package com.duc.crqa.user.entity;

import com.duc.crqa.common.entity.AuditedEntity;
import com.duc.crqa.common.enums.Role;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "IT_users")
@Getter
@Setter
@NoArgsConstructor
public class User extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "IT_users_seq_gen")
    @SequenceGenerator(name = "IT_users_seq_gen", sequenceName = "IT_USERS_SEQ", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(length = 255)
    private String email;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false)
    private boolean active = true;

    /** Whether this user wants the notification email as well as the in-app notification. */
    @Column(name = "email_notifications", nullable = false)
    private boolean emailNotifications = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "IT_user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 50)
    private Set<Role> roles = new HashSet<>();

    public boolean hasRole(Role role) {
        return roles != null && roles.contains(role);
    }

    public String displayLabel() {
        return displayName == null || displayName.isBlank() ? username : displayName;
    }
}
