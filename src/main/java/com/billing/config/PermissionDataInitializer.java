package com.billing.config;

import com.billing.entity.AppMenu;
import com.billing.entity.AppMenuAction;
import com.billing.entity.Company;
import com.billing.entity.CompanyOwner;
import com.billing.entity.CompanyThemeSetting;
import com.billing.entity.RoleMaster;
import com.billing.entity.RoleMenuActionPermission;
import com.billing.entity.RoleMenuPermission;
import com.billing.repository.AppMenuActionRepository;
import com.billing.repository.AppMenuRepository;
import com.billing.repository.CompanyRepository;
import com.billing.repository.CompanyOwnerRepository;
import com.billing.repository.RoleMasterRepository;
import com.billing.repository.CompanyThemeSettingRepository;
import com.billing.repository.RoleMenuActionPermissionRepository;
import com.billing.repository.RoleMenuPermissionRepository;
import com.billing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PermissionDataInitializer implements ApplicationRunner {

    private final AppMenuRepository appMenuRepository;
    private final AppMenuActionRepository appMenuActionRepository;
    private final CompanyRepository companyRepository;
    private final CompanyOwnerRepository companyOwnerRepository;
    private final CompanyThemeSettingRepository companyThemeSettingRepository;
    private final RoleMasterRepository roleMasterRepository;
    private final RoleMenuPermissionRepository roleMenuPermissionRepository;
    private final RoleMenuActionPermissionRepository roleMenuActionPermissionRepository;
    private final UserRepository userRepository;

    private static final List<MenuSeed> MENUS = List.of(
            new MenuSeed("Dashboard", "DASHBOARD", "LayoutDashboard", "/dashboard", 1, null),
            new MenuSeed("Customers", "CUSTOMERS", "Users", "/customers", 2, null),
            new MenuSeed("Products", "PRODUCTS", "Boxes", "/products", 3, null),
            new MenuSeed("Create Invoice", "CREATE_INVOICE", "FilePlus2", "/create-invoice", 4, null),
            new MenuSeed("Invoices", "INVOICES", "FileText", "/invoices", 5, null),
            new MenuSeed("Payments", "PAYMENTS", "CreditCard", "/payments", 6, null),
            new MenuSeed("Outstanding", "OUTSTANDING", "Wallet", "/outstanding", 7, null),
            new MenuSeed("Analytics", "ANALYTICS", "BarChart3", "/analytics", 8, null),
            new MenuSeed("Setup", "SETUP", "Settings", "/setup", 9, null),
            new MenuSeed("Users", "USERS", "Users", "/setup/users", 10, "SETUP"),
            new MenuSeed("Product Categories", "PRODUCT_CATEGORY", "Tags", "/setup/product-categories", 11, "SETUP"),
            new MenuSeed("Theme Settings", "THEME_SETTINGS", "Palette", "/setup/theme-settings", 12, "SETUP"),
            new MenuSeed("About Company", "ABOUT_COMPANY", "Building2", "/setup/about-company", 13, "SETUP"),
            new MenuSeed("Role Permissions", "ROLE_PERMISSIONS", "ShieldCheck", "/setup/role-permissions", 14, "SETUP")
    );

    private static final List<ActionSeed> ACTIONS = List.of(
            new ActionSeed("View", "VIEW"),
            new ActionSeed("Add", "ADD"),
            new ActionSeed("Edit", "EDIT"),
            new ActionSeed("Delete", "DELETE"),
            new ActionSeed("Export", "EXPORT")
    );

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedRoles();
        seedMenus();
        seedActions();
        for (Company company : companyRepository.findAll()) {
            seedPermissionsForCompany(company);
            seedOwnersForCompany(company);
            seedThemeForCompany(company);
        }
    }

    private void seedRoles() {
        saveRole("Owner", "OWNER");
        saveRole("Admin", "ADMIN");
        saveRole("User", "USER");
    }

    private void saveRole(String name, String code) {
        roleMasterRepository.findByRoleCode(code)
                .orElseGet(() -> roleMasterRepository.save(RoleMaster.builder()
                        .roleName(name)
                        .roleCode(code)
                        .systemRole(true)
                        .build()));
    }

    private void seedMenus() {
        for (MenuSeed seed : MENUS) {
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
    }

    private void seedActions() {
        for (AppMenu menu : appMenuRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc()) {
            for (ActionSeed seed : ACTIONS) {
                if (appMenuActionRepository.findByAppMenuAndActionCode(menu, seed.code()).isEmpty()) {
                    appMenuActionRepository.save(AppMenuAction.builder()
                            .appMenu(menu)
                            .actionName(seed.name())
                            .actionCode(seed.code())
                            .active(true)
                            .build());
                }
            }
        }
    }

    public void seedPermissionsForCompany(Company company) {
        Map<String, Set<String>> visibleMenusByRole = Map.of(
                "OWNER", Set.of("DASHBOARD", "CUSTOMERS", "PRODUCTS", "CREATE_INVOICE", "INVOICES", "PAYMENTS", "OUTSTANDING", "ANALYTICS", "SETUP", "USERS", "PRODUCT_CATEGORY", "THEME_SETTINGS", "ABOUT_COMPANY", "ROLE_PERMISSIONS"),
                "ADMIN", Set.of("DASHBOARD", "CUSTOMERS", "PRODUCTS", "CREATE_INVOICE", "INVOICES", "PAYMENTS", "OUTSTANDING", "ANALYTICS", "PRODUCT_CATEGORY", "ABOUT_COMPANY"),
                "USER", Set.of("DASHBOARD", "CUSTOMERS", "PRODUCTS", "CREATE_INVOICE", "INVOICES", "OUTSTANDING", "ANALYTICS", "ABOUT_COMPANY")
        );
        Map<String, Set<String>> actionCodesByRole = Map.of(
                "OWNER", Set.of("VIEW", "ADD", "EDIT", "DELETE", "EXPORT"),
                "ADMIN", Set.of("VIEW", "ADD", "EDIT", "DELETE", "EXPORT"),
                "USER", Set.of("VIEW")
        );

        for (RoleMaster role : roleMasterRepository.findAll()) {
            Set<String> visibleMenus = visibleMenusByRole.getOrDefault(role.getRoleCode(), Set.of());
            Set<String> allowedActions = actionCodesByRole.getOrDefault(role.getRoleCode(), Set.of());
            for (AppMenu menu : appMenuRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc()) {
                boolean canView = visibleMenus.contains(menu.getMenuCode());
                roleMenuPermissionRepository.findByCompanyAndRoleAndAppMenu(company, role, menu)
                        .ifPresentOrElse(existing -> {
                            if ("PRODUCT_CATEGORY".equals(menu.getMenuCode()) && !"OWNER".equals(role.getRoleCode())) {
                                existing.setCanView("ADMIN".equals(role.getRoleCode()));
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
                    boolean allowed = canView && allowedActions.contains(action.getActionCode());
                    if ("PRODUCT_CATEGORY".equals(menu.getMenuCode()) && !"OWNER".equals(role.getRoleCode())) {
                        allowed = "VIEW".equals(action.getActionCode());
                    }
                    if (("ABOUT_COMPANY".equals(menu.getMenuCode()) || "THEME_SETTINGS".equals(menu.getMenuCode())) && !"OWNER".equals(role.getRoleCode())) {
                        allowed = "ABOUT_COMPANY".equals(menu.getMenuCode()) && "VIEW".equals(action.getActionCode());
                    }
                    if ("USER".equals(role.getRoleCode()) && "CREATE_INVOICE".equals(menu.getMenuCode()) && "ADD".equals(action.getActionCode())) {
                        allowed = true;
                    }
                    boolean actionAllowed = allowed;
                    roleMenuActionPermissionRepository.findByCompanyAndRoleAndAppMenuAndAppMenuAction(company, role, menu, action)
                            .ifPresentOrElse(existing -> {
                                if ("PRODUCT_CATEGORY".equals(menu.getMenuCode()) && !"OWNER".equals(role.getRoleCode())) {
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

    public void seedOwnersForCompany(Company company) {
        userRepository.findByCompanyOrderByCreatedAtDesc(company).stream()
                .filter(user -> "OWNER".equals(user.getRole().name()))
                .forEach(user -> companyOwnerRepository.findByCompanyAndUser(company, user)
                        .orElseGet(() -> companyOwnerRepository.save(CompanyOwner.builder()
                                .company(company)
                                .user(user)
                                .build())));
    }

    public void seedThemeForCompany(Company company) {
        companyThemeSettingRepository.findByCompany(company)
                .orElseGet(() -> companyThemeSettingRepository.save(CompanyThemeSetting.builder()
                        .company(company)
                        .themeColor("#0EA5E9")
                        .build()));
    }

    private record MenuSeed(String name, String code, String icon, String route, int order, String parentCode) {
    }

    private record ActionSeed(String name, String code) {
    }
}
