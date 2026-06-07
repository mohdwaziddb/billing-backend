package com.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "role_menu_action_permission", uniqueConstraints = @UniqueConstraint(name = "uk_role_menu_action_permission", columnNames = {"company_id", "role_id", "app_menu_id", "app_menu_action_id"}))
public class RoleMenuActionPermission extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private RoleMaster role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_menu_id", nullable = false)
    private AppMenu appMenu;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_menu_action_id", nullable = false)
    private AppMenuAction appMenuAction;

    @Builder.Default
    @Column(name = "is_allowed", nullable = false)
    private boolean allowed = false;
}
