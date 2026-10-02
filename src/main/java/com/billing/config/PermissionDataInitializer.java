package com.billing.config;

import com.billing.entity.AppMenu;
import com.billing.entity.AppMenuAction;
import com.billing.entity.Company;
import com.billing.entity.CompanyThemeSetting;
import com.billing.entity.NotificationChannel;
import com.billing.entity.PlatformSetting;
import com.billing.entity.RoleMaster;
import com.billing.entity.RoleMenuActionPermission;
import com.billing.entity.RoleMenuPermission;
import com.billing.repository.AppMenuActionRepository;
import com.billing.repository.AppMenuRepository;
import com.billing.repository.CompanyRepository;
import com.billing.repository.RoleMasterRepository;
import com.billing.repository.CompanyThemeSettingRepository;
import com.billing.repository.NotificationChannelRepository;
import com.billing.repository.PlatformSettingRepository;
import com.billing.repository.RoleMenuActionPermissionRepository;
import com.billing.repository.RoleMenuPermissionRepository;
import com.billing.service.TaxMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
@Component
@RequiredArgsConstructor
public class PermissionDataInitializer implements ApplicationRunner {

    private final AppMenuRepository appMenuRepository;
    private final AppMenuActionRepository appMenuActionRepository;
    private final CompanyRepository companyRepository;
    private final CompanyThemeSettingRepository companyThemeSettingRepository;
    private final NotificationChannelRepository notificationChannelRepository;
    private final PlatformSettingRepository platformSettingRepository;
    private final RoleMasterRepository roleMasterRepository;
    private final RoleMenuPermissionRepository roleMenuPermissionRepository;
    private final RoleMenuActionPermissionRepository roleMenuActionPermissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TaxMasterService taxMasterService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedRoles();
        seedPlatformSettings();
        seedMenus();
        seedActions();
        for (Company company : companyRepository.findAll()) {
            seedPermissionsForCompany(company);
            seedThemeForCompany(company);
            seedDefaultNotificationChannels(company);
            taxMasterService.createDefaultTaxesForCompany(company);
        }
    }

    private void seedRoles() {
        for (MasterSeedCatalog.RoleSeed role : MasterSeedCatalog.ROLES) {
            saveRole(role.name(), role.code());
        }
    }

    private void saveRole(String name, String code) {
        roleMasterRepository.findByRoleCode(code)
                .orElseGet(() -> roleMasterRepository.save(RoleMaster.builder()
                        .roleName(name)
                        .roleCode(code)
                        .systemRole(true)
                        .build()));
    }

    private void seedPlatformSettings() {
        PlatformSetting setting = platformSettingRepository.findTopByOrderByIdAsc()
                .orElseGet(() -> platformSettingRepository.save(PlatformSetting.builder()
                        .platformName("Bizio")
                        .platformTagline("Empowering Businesses, Simplifying Growth")
                        .username("mohdwaziddb")
                        .password(passwordEncoder.encode("123456789"))
                        .build()));

        boolean updated = false;
        if (setting.getUsername() == null || setting.getUsername().isBlank()) {
            setting.setUsername("mohdwaziddb");
            updated = true;
        }
        if (setting.getPassword() == null || setting.getPassword().isBlank()) {
            setting.setPassword(passwordEncoder.encode("123456789"));
            updated = true;
        } else if (!isEncodedPassword(setting.getPassword())) {
            setting.setPassword(passwordEncoder.encode(setting.getPassword().trim()));
            updated = true;
        }
        if (updated) {
            platformSettingRepository.save(setting);
        }
    }

    private void seedMenus() {
        for (MasterSeedCatalog.MenuSeed seed : MasterSeedCatalog.MENUS) {
            AppMenu parent = seed.parentCode() == null ? null : appMenuRepository.findByMenuCode(seed.parentCode()).orElse(null);
            AppMenu menu = appMenuRepository.findByMenuCode(seed.code())
                    .orElseGet(() -> appMenuRepository.save(AppMenu.builder()
                        .menuName(seed.name())
                        .menuCode(seed.code())
                        .menuIcon(seed.icon())
                        .menuRoute(seed.route())
                        .displayOrder(seed.order())
                        .parentMenu(parent)
                        .active(true)
                        .build()));
            menu.setMenuName(seed.name());
            menu.setMenuIcon(seed.icon());
            menu.setMenuRoute(seed.route());
            menu.setDisplayOrder(seed.order());
            menu.setParentMenu(parent);
            appMenuRepository.save(menu);
        }
        for (String retiredCode : MasterSeedCatalog.RETIRED_MENU_CODES) {
            retireMenu(retiredCode);
        }
    }

    private void seedActions() {
        for (AppMenu menu : appMenuRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc()) {
            for (MasterSeedCatalog.ActionSeed seed : MasterSeedCatalog.ACTIONS) {
                appMenuActionRepository.findByAppMenuAndActionCode(menu, seed.code())
                        .ifPresentOrElse(existing -> {
                            existing.setActionName(actionName(menu, seed));
                            appMenuActionRepository.save(existing);
                        }, () -> appMenuActionRepository.save(AppMenuAction.builder()
                            .appMenu(menu)
                            .actionName(actionName(menu, seed))
                            .actionCode(seed.code())
                            .active(true)
                            .build()));
            }
        }
    }

    /**
     * Seed tenant-independent base masters inside a NEW tenant database.
     * Must run BEFORE seedPermissionsForCompany (which loops roles x menus).
     * All steps are find-or-create: safe to re-run (repair flows).
     * Tenant context must already be switched by the caller; this method
     * starts no transaction itself (joins the caller's).
     */
    public void seedBaseMasters() {
        seedRoles();
        seedMenus();
        seedActions();
    }

    /**
     * Verification gate for provisioning: throws when the tenant is missing
     * base masters. Called inside the provisioning transaction so a failure
     * rolls the tenant writes back (outer layer drops the database + registry row).
     */
    public void verifyTenantSeeded(Company company) {
        List<String> missing = new ArrayList<>();
        if (roleMasterRepository.count() < 3) {
            missing.add("role_master");
        }
        if (appMenuRepository.count() < MasterSeedCatalog.MENUS.size()) {
            missing.add("app_menu");
        }
        if (appMenuActionRepository.count() < 1) {
            missing.add("app_menu_action");
        }
        if (roleMenuPermissionRepository.count() < 1) {
            missing.add("role_menu_permission");
        }
        if (companyThemeSettingRepository.count() < 1) {
            missing.add("company_theme_setting");
        }
        if (notificationChannelRepository.count() < 1) {
            missing.add("notification_channel");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Tenant seeding incomplete: " + String.join(",", missing));
        }
    }

    public void seedPermissionsForCompany(Company company) {
        // Single source: MasterSeedCatalog (matrices + algorithm shared with sample.sql generator).
        for (RoleMaster role : roleMasterRepository.findAll()) {
            String roleCode = role.getRoleCode();
            for (AppMenu menu : appMenuRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc()) {
                boolean canView = MasterSeedCatalog.isMenuVisible(roleCode, menu.getMenuCode());
                roleMenuPermissionRepository.findByCompanyAndRoleAndAppMenu(company, role, menu)
                        .ifPresentOrElse(existing -> {
                            if (MasterSeedCatalog.RESTRICTED_OWNER_ONLY_MENUS.contains(menu.getMenuCode()) && !"OWNER".equals(roleCode)) {
                                existing.setCanView("ADMIN".equals(roleCode));
                                roleMenuPermissionRepository.save(existing);
                            }
                        }, () -> {
                    RoleMenuPermission menuPermission = RoleMenuPermission.builder()
                            .company(company)
                            .role(role)
                            .appMenu(menu)
                            .canView(canView)
                            .build();
                    roleMenuPermissionRepository.save(menuPermission);
                });

                for (AppMenuAction action : appMenuActionRepository.findByAppMenuAndActiveTrueOrderByIdAsc(menu)) {
                    boolean actionAllowed = MasterSeedCatalog.isActionAllowed(roleCode, menu.getMenuCode(), action.getActionCode());
                    roleMenuActionPermissionRepository.findByCompanyAndRoleAndAppMenuAndAppMenuAction(company, role, menu, action)
                            .ifPresentOrElse(existing -> {
                                if (MasterSeedCatalog.RESTRICTED_OWNER_ONLY_MENUS.contains(menu.getMenuCode()) && !"OWNER".equals(roleCode)) {
                                    existing.setAllowed("VIEW".equals(action.getActionCode()));
                                    roleMenuActionPermissionRepository.save(existing);
                                }
                            }, () -> {
                        RoleMenuActionPermission actionPermission = RoleMenuActionPermission.builder()
                                .company(company)
                                .role(role)
                                .appMenu(menu)
                                .appMenuAction(action)
                                .allowed(actionAllowed)
                                .build();
                        roleMenuActionPermissionRepository.save(actionPermission);
                    });
                }
            }
        }
    }

    public void seedThemeForCompany(Company company) {
        companyThemeSettingRepository.findByCompany(company)
                .orElseGet(() -> companyThemeSettingRepository.save(CompanyThemeSetting.builder()
                        .company(company)
                        .themeColor(MasterSeedCatalog.DEFAULT_THEME_COLOR)
                        .build()));
    }

    private String actionName(AppMenu menu, MasterSeedCatalog.ActionSeed seed) {
        return seed.name();
    }

    private void retireMenu(String menuCode) {
        appMenuRepository.findByMenuCode(menuCode).ifPresent(menu -> {
            menu.setActive(false);
            appMenuRepository.save(menu);
        });
    }

    public void seedDefaultNotificationChannels(Company company) {
        for (String channelName : MasterSeedCatalog.DEFAULT_NOTIFICATION_CHANNELS) {
            if (notificationChannelRepository.existsByCompanyAndChannelNameIgnoreCase(company, channelName)) {
                continue;
            }
            notificationChannelRepository.save(NotificationChannel.builder()
                    .company(company)
                    .channelName(channelName)
                    .defaultChannel(true)
                    .active(true)
                    .build());
        }
    }

    private boolean isEncodedPassword(String value) {
        return value.startsWith("$2a$")
                || value.startsWith("$2b$")
                || value.startsWith("$2y$");
    }
}
