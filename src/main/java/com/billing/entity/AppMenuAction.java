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
@Table(name = "app_menu_action", uniqueConstraints = @UniqueConstraint(name = "uk_menu_action_code", columnNames = {"app_menu_id", "action_code"}))
public class AppMenuAction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_menu_id", nullable = false)
    private AppMenu appMenu;

    @Column(nullable = false)
    private String actionName;

    @Column(nullable = false)
    private String actionCode;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
