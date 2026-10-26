package com.billing.service;

import com.billing.dto.permission.ActionPermissionRequest;
import com.billing.dto.permission.ActionPermissionResponse;
import com.billing.dto.permission.MenuPermissionRequest;
import com.billing.dto.permission.MenuPermissionResponse;
import com.billing.dto.permission.PermissionMatrixRequest;
import com.billing.dto.permission.PermissionMatrixResponse;
import com.billing.entity.AppMenu;
import com.billing.entity.AppMenuAction;
import com.billing.entity.Company;
import com.billing.entity.RoleMaster;
import com.billing.entity.RoleMenuActionPermission;
import com.billing.entity.RoleMenuPermission;
import com.billing.entity.User;
import com.billing.entity.UserPermission;
import com.billing.entity.enums.RoleName;
import com.billing.exception.ResourceNotFoundException;
import com.billing.repository.AppMenuActionRepository;
import com.billing.repository.AppMenuRepository;
import com.billing.repository.RoleMasterRepository;
import com.billing.repository.RoleMenuActionPermissionRepository;
import com.billing.repository.RoleMenuPermissionRepository;
import com.billing.repository.UserPermissionRepository;
import com.billing.repository.UserRepository;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service("permissionChecker")
@RequiredArgsConstructor
public class PermissionService {
    private static final String LEGACY_PLATFORM_ADMIN_MENU_CODE_PREFIX = "SUPER" + "_ADMIN_";

    private final AppMenuRepository appMenuRepository;
    private final AppMenuActionRepository appMenuActionRepository;
    private final RoleMasterRepository roleMasterRepository;
    private final RoleMenuPermissionRepository roleMenuPermissionRepository;
    private final RoleMenuActionPermissionRepository roleMenuActionPermissionRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final UserRepository userRepository;
    private final AccessControlService accessControlService;

    @Transactional(readOnly = true)
    public boolean has(Authentication authentication, String menuCode, String actionCode) {
        if (authentication == null || authentication.getName() == null) {
            return false;
        }
        // Tenant super-admin: full rights, no permission rows needed.
        if (authentication.getPrincipal() instanceof com.billing.security.TenantSuperAdminPrincipal) {
            return true;
        }
        // Platform admin tenant ka user nahi hai: username se tenant DB me lookup karte to
        // kisi tenant ke same-naam wale user ke rights mil jaate. Uske APIs alag (platform-admin).
        if (authentication.getPrincipal() instanceof com.billing.security.PlatformAdminPrincipal) {
            return false;
        }
        return has(authentication.getName(), menuCode, actionCode);
    }

    @Transactional(readOnly = true)
    public boolean has(String email, String menuCode, String actionCode) {
        User user = accessControlService.getCurrentUser(email);
        // DATABASE-per-tenant: User.company is @Transient, resolve from tenant connection.
        Company company = accessControlService.requireCompany(user);
        // Har @RequiresPermission call par chalta hai: user/company dobara fetch mat karo
        // (pehle hasPermission(id, id) user + company phir se laata tha, isCompanyOwner ek baar aur).
        return evaluatePermission(user, company, menuCode, actionCode);
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(Long userId, Long companyId, String menuCode, String actionCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        // DATABASE-per-tenant: User.company is @Transient, resolve from tenant connection.
        Company company = accessControlService.requireCompany(user);
        if (!company.getId().equals(companyId)) {
            return false;
        }
        return evaluatePermission(user, company, menuCode, actionCode);
    }

    /** user + company pehle se resolved (requireCompany ho chuka) — sirf menu/action/role checks. */
    private boolean evaluatePermission(User user, Company company, String menuCode, String actionCode) {
        AppMenu menu = appMenuRepository.findByMenuCode(menuCode).orElse(null);
        if (menu == null || !menu.isActive() || isLegacySuperAdminMenu(menu)) {
            return false;
        }
        AppMenuAction action = appMenuActionRepository.findByAppMenuAndActionCode(menu, actionCode).orElse(null);
        if (action == null || !action.isActive()) {
            return false;
        }
        // = accessControlService.isCompanyOwner(user); company caller resolve kar chuka hai.
        if (user.isActive() && user.getRole() == RoleName.OWNER) {
            return true;
        }
        RoleMaster role = roleMasterRepository.findByRoleCode(roleCode(user)).orElse(null);
        if (role == null) {
            return false;
        }
        if ("VIEW".equalsIgnoreCase(actionCode)) {
            Optional<UserPermission> override = userPermissionRepository.findByCompanyAndUserAndAppMenuAndAppMenuAction(company, user, menu, action);
            if (override.isPresent()) {
                return override.get().isAllowed();
            }
            Optional<RoleMenuPermission> rolePermission = roleMenuPermissionRepository.findByCompanyAndRoleAndAppMenu(company, role, menu);
            return rolePermission.map(RoleMenuPermission::isCanView).orElse(false);
        }

        Optional<UserPermission> override = userPermissionRepository.findByCompanyAndUserAndAppMenuAndAppMenuAction(company, user, menu, action);
        if (override.isPresent()) {
            return override.get().isAllowed();
        }
        return roleMenuActionPermissionRepository.findByCompanyAndRoleAndAppMenuAndAppMenuAction(company, role, menu, action)
                .map(RoleMenuActionPermission::isAllowed)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public PermissionMatrixResponse effectivePermissions(String email) {
        User user = accessControlService.getCurrentUser(email);
        // DATABASE-per-tenant: User.company is @Transient, resolve from tenant connection.
        Company tenantCompany = accessControlService.requireCompany(user);
        if (accessControlService.isCompanyOwner(user)) {
            return PermissionMatrixResponse.builder()
                    .roleCode(roleCode(user))
                    .userId(user.getId())
                    .menus(toHierarchy(allMenus(true, user, null, tenantCompany, true)))
                    .build();
        }
        RoleMaster role = roleMasterRepository.findByRoleCode(roleCode(user))
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
        return PermissionMatrixResponse.builder()
                .roleCode(roleCode(user))
                .userId(user.getId())
                .menus(toHierarchy(allMenus(false, user, role, tenantCompany, true)))
                .build();
    }

    @Transactional(readOnly = true)
    public PermissionMatrixResponse roleMatrix(String ownerEmail, String roleCode) {
        Company company = requireOwnerCompany(ownerEmail);
        RoleMaster role = roleMasterRepository.findByRoleCode(roleCode)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
        return PermissionMatrixResponse.builder()
                .roleCode(role.getRoleCode())
                .menus(roleMenus(company, role))
                .build();
    }

    @Transactional(readOnly = true)
    public PermissionMatrixResponse userMatrix(String ownerEmail, Long userId) {
        Company company = requireOwnerCompany(ownerEmail);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        RoleMaster role = roleMasterRepository.findByRoleCode(roleCode(user))
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
        return PermissionMatrixResponse.builder()
                .roleCode(role.getRoleCode())
                .userId(user.getId())
                .menus(userMenus(company, user, role))
                .build();
    }

    @Transactional
    public PermissionMatrixResponse saveRoleMatrix(String ownerEmail, PermissionMatrixRequest request) {
        Company company = requireOwnerCompany(ownerEmail);
        RoleMaster role = roleMasterRepository.findByRoleCode(request.getRoleCode())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
        if ("OWNER".equals(role.getRoleCode())) {
            return roleMatrix(ownerEmail, role.getRoleCode());
        }
        Map<Long, AppMenu> menus = appMenuRepository.findAll().stream().collect(Collectors.toMap(AppMenu::getId, Function.identity()));
        Map<Long, AppMenuAction> actions = appMenuActionRepository.findAll().stream().collect(Collectors.toMap(AppMenuAction::getId, Function.identity()));
        Map<Long, RoleMenuPermission> menuPermissions = roleMenuPermissionsByMenu(company, role);
        Map<Long, RoleMenuActionPermission> actionPermissions = roleActionPermissionsByAction(company, role);
        for (MenuPermissionRequest menuRequest : request.getMenus()) {
            AppMenu menu = requireMenu(menus, menuRequest.getMenuId());
            if (isLegacySuperAdminMenu(menu)) {
                continue;
            }
            RoleMenuPermission menuPermission = Optional.ofNullable(menuPermissions.get(menu.getId()))
                    .orElse(RoleMenuPermission.builder().company(company).role(role).appMenu(menu).build());
            menuPermission.setCanView(menuRequest.isCanView());
            roleMenuPermissionRepository.save(menuPermission);
            for (ActionPermissionRequest actionRequest : menuRequest.getActions()) {
                AppMenuAction action = requireAction(actions, actionRequest.getActionId(), menu);
                RoleMenuActionPermission actionPermission = Optional.ofNullable(actionPermissions.get(action.getId()))
                        .orElse(RoleMenuActionPermission.builder().company(company).role(role).appMenu(menu).appMenuAction(action).build());
                actionPermission.setAllowed(actionRequest.isAllowed());
                roleMenuActionPermissionRepository.save(actionPermission);
            }
        }
        return roleMatrix(ownerEmail, role.getRoleCode());
    }

    @Transactional
    public PermissionMatrixResponse saveUserMatrix(String ownerEmail, PermissionMatrixRequest request) {
        Company company = requireOwnerCompany(ownerEmail);
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (accessControlService.isCompanyOwner(user)) {
            return userMatrix(ownerEmail, user.getId());
        }
        Map<Long, AppMenu> menus = appMenuRepository.findAll().stream().collect(Collectors.toMap(AppMenu::getId, Function.identity()));
        Map<Long, AppMenuAction> actions = appMenuActionRepository.findAll().stream().collect(Collectors.toMap(AppMenuAction::getId, Function.identity()));
        Map<Long, UserPermission> overrides = userPermissionsByAction(company, user);
        for (MenuPermissionRequest menuRequest : request.getMenus()) {
            AppMenu menu = requireMenu(menus, menuRequest.getMenuId());
            if (isLegacySuperAdminMenu(menu)) {
                continue;
            }
            for (ActionPermissionRequest actionRequest : menuRequest.getActions()) {
                AppMenuAction action = requireAction(actions, actionRequest.getActionId(), menu);
                if (actionRequest.getOverrideAllowed() == null) {
                    Optional.ofNullable(overrides.get(action.getId())).ifPresent(userPermissionRepository::delete);
                    continue;
                }
                UserPermission permission = Optional.ofNullable(overrides.get(action.getId()))
                        .orElse(UserPermission.builder().company(company).user(user).appMenu(menu).appMenuAction(action).build());
                permission.setAllowed(actionRequest.getOverrideAllowed());
                userPermissionRepository.save(permission);
            }
        }
        return userMatrix(ownerEmail, user.getId());
    }

    @Transactional(readOnly = true)
    public PermissionMatrixResponse roleMatrix(Map<String, Object> param, String ownerEmail) {
        String roleCode = DataTypeUtility.stringValue(param.get("roleCode"));
        if (roleCode.length() == 0) {
            roleCode = DataTypeUtility.stringValue(param.get("role_code"));
        }
        if (roleCode.length() == 0) {
            roleCode = null;
        }
        return roleMatrix(ownerEmail, roleCode);
    }

    @Transactional(readOnly = true)
    public PermissionMatrixResponse userMatrix(Map<String, Object> param, String ownerEmail) {
        Long userId = DataTypeUtility.getForeignKeyValue(param.get("userId"));
        if (userId == null) {
            userId = DataTypeUtility.getForeignKeyValue(param.get("user_id"));
        }
        return userMatrix(ownerEmail, userId);
    }

    @Transactional
    public PermissionMatrixResponse saveRoleMatrix(Map<String, Object> param, String ownerEmail) {
        PermissionMatrixRequest permissionMatrixRequest = mapToPermissionMatrixRequest(param);
        return saveRoleMatrix(ownerEmail, permissionMatrixRequest);
    }

    @Transactional
    public PermissionMatrixResponse saveUserMatrix(Map<String, Object> param, String ownerEmail) {
        PermissionMatrixRequest permissionMatrixRequest = mapToPermissionMatrixRequest(param);
        return saveUserMatrix(ownerEmail, permissionMatrixRequest);
    }

    private PermissionMatrixRequest mapToPermissionMatrixRequest(Map<String, Object> param) {
        PermissionMatrixRequest request = new PermissionMatrixRequest();
        String roleCode = DataTypeUtility.stringValue(param.get("roleCode"));
        if (roleCode.length() == 0) {
            roleCode = DataTypeUtility.stringValue(param.get("role_code"));
        }
        if (roleCode.length() > 0) {
            request.setRoleCode(roleCode);
        }
        Long userId = DataTypeUtility.getForeignKeyValue(param.get("userId"));
        if (userId == null) {
            userId = DataTypeUtility.getForeignKeyValue(param.get("user_id"));
        }
        request.setUserId(userId);
        Object menusObj = param.get("menus");
        if (menusObj instanceof List<?> && DataTypeUtility.requireNonNullCollection((Collection<?>) menusObj)) {
            List<MenuPermissionRequest> menus = new ArrayList<>();
            for (Object menuObj : (List<?>) menusObj) {
                if (menuObj instanceof Map<?, ?>) {
                    Map<?, ?> menuMap = (Map<?, ?>) menuObj;
                    MenuPermissionRequest menuRequest = new MenuPermissionRequest();
                    Object menuIdRaw = menuMap.get("menuId");
                    if (menuIdRaw == null) {
                        menuIdRaw = menuMap.get("menu_id");
                    }
                    if (menuIdRaw == null) {
                        menuIdRaw = menuMap.get("id");
                    }
                    menuRequest.setMenuId(DataTypeUtility.getForeignKeyValue(menuIdRaw));
                    Object canViewRaw = menuMap.get("canView");
                    if (canViewRaw == null) {
                        canViewRaw = menuMap.get("can_view");
                    }
                    menuRequest.setCanView(DataTypeUtility.booleanValue(canViewRaw));
                    Object actionsObj = menuMap.get("actions");
                    List<ActionPermissionRequest> actions = new ArrayList<>();
                    if (actionsObj instanceof List<?> && DataTypeUtility.requireNonNullCollection((Collection<?>) actionsObj)) {
                        for (Object actionObj : (List<?>) actionsObj) {
                            if (actionObj instanceof Map<?, ?>) {
                                Map<?, ?> actionMap = (Map<?, ?>) actionObj;
                                ActionPermissionRequest actionRequest = new ActionPermissionRequest();
                                Object actionIdRaw = actionMap.get("actionId");
                                if (actionIdRaw == null) {
                                    actionIdRaw = actionMap.get("action_id");
                                }
                                if (actionIdRaw == null) {
                                    actionIdRaw = actionMap.get("id");
                                }
                                actionRequest.setActionId(DataTypeUtility.getForeignKeyValue(actionIdRaw));
                                Object allowedRaw = actionMap.get("allowed");
                                actionRequest.setAllowed(DataTypeUtility.booleanValue(allowedRaw));
                                Object overrideRaw = actionMap.get("overrideAllowed");
                                if (overrideRaw == null) {
                                    overrideRaw = actionMap.get("override_allowed");
                                }
                                if (overrideRaw != null) {
                                    String overrideStr = DataTypeUtility.stringValue(overrideRaw);
                                    if (overrideStr.length() > 0) {
                                        if ("null".equalsIgnoreCase(overrideStr)) {
                                            actionRequest.setOverrideAllowed(null);
                                        } else {
                                            actionRequest.setOverrideAllowed(DataTypeUtility.booleanValue(overrideRaw));
                                        }
                                    } else {
                                        actionRequest.setOverrideAllowed(null);
                                    }
                                } else {
                                    actionRequest.setOverrideAllowed(null);
                                }
                                actions.add(actionRequest);
                            }
                        }
                    }
                    menuRequest.setActions(actions);
                    menus.add(menuRequest);
                }
            }
            request.setMenus(menus);
        }
        return request;
    }

    // Matrix ek baar me: menus, actions, role rows aur user overrides kuch bulk queries me load,
    // phir memory me map. Pehle har menu/action par alag query thi (ek matrix = ~700-1900 queries).
    private List<MenuPermissionResponse> allMenus(boolean allowAll, User user, RoleMaster role, Company company, boolean onlyVisible) {
        Map<Long, List<AppMenuAction>> actionsByMenu = activeActionsByMenu();
        if (allowAll) {
            return activeMenus().stream()
                    .map(menu -> toMenuResponse(menu, true, actionsOf(actionsByMenu, menu), null, true))
                    .toList();
        }
        EffectivePermissions effective = effectivePermissions(company, user, role);
        return activeMenus().stream()
                .map(menu -> effectiveMenuResponse(effective, actionsOf(actionsByMenu, menu), menu))
                .filter(menu -> !onlyVisible || menu.isCanView())
                .toList();
    }

    private List<MenuPermissionResponse> roleMenus(Company company, RoleMaster role) {
        boolean owner = "OWNER".equals(role.getRoleCode());
        Map<Long, List<AppMenuAction>> actionsByMenu = activeActionsByMenu();
        Map<Long, RoleMenuPermission> menuPermissions = owner ? Map.of() : roleMenuPermissionsByMenu(company, role);
        Map<Long, RoleMenuActionPermission> actionPermissions = owner ? Map.of() : roleActionPermissionsByAction(company, role);
        return activeMenus().stream()
                .map(menu -> {
                    RoleMenuPermission menuPermission = menuPermissions.get(menu.getId());
                    boolean canView = owner || (menuPermission != null && menuPermission.isCanView());
                    return toMenuResponse(menu, canView, actionsOf(actionsByMenu, menu), action -> {
                        RoleMenuActionPermission permission = actionPermissions.get(action.getId());
                        return owner || (permission != null && permission.isAllowed());
                    }, false);
                })
                .toList();
    }

    private List<MenuPermissionResponse> userMenus(Company company, User user, RoleMaster role) {
        Map<Long, List<AppMenuAction>> actionsByMenu = activeActionsByMenu();
        EffectivePermissions effective = effectivePermissions(company, user, role);
        return activeMenus().stream()
                .map(menu -> effectiveMenuResponse(effective, actionsOf(actionsByMenu, menu), menu))
                .toList();
    }

    private record EffectivePermissions(Map<Long, RoleMenuPermission> roleMenus,
                                        Map<Long, RoleMenuActionPermission> roleActions,
                                        Map<Long, UserPermission> userOverrides) {
    }

    private EffectivePermissions effectivePermissions(Company company, User user, RoleMaster role) {
        return new EffectivePermissions(roleMenuPermissionsByMenu(company, role),
                roleActionPermissionsByAction(company, role),
                userPermissionsByAction(company, user));
    }

    private MenuPermissionResponse effectiveMenuResponse(EffectivePermissions effective, List<AppMenuAction> actions, AppMenu menu) {
        RoleMenuPermission menuPermission = effective.roleMenus().get(menu.getId());
        boolean canView = menuPermission != null && menuPermission.isCanView();
        Optional<AppMenuAction> viewAction = actions.stream().filter(action -> "VIEW".equals(action.getActionCode())).findFirst();
        if (viewAction.isPresent()) {
            UserPermission override = effective.userOverrides().get(viewAction.get().getId());
            if (override != null) {
                canView = override.isAllowed();
            }
        }
        return toMenuResponse(menu, canView, actions, action -> {
            UserPermission override = effective.userOverrides().get(action.getId());
            if (override != null) {
                return override.isAllowed();
            }
            RoleMenuActionPermission permission = effective.roleActions().get(action.getId());
            return permission != null && permission.isAllowed();
        }, false, action -> {
            UserPermission override = effective.userOverrides().get(action.getId());
            return override == null ? null : override.isAllowed();
        });
    }

    private List<AppMenu> activeMenus() {
        return appMenuRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                .filter(menu -> !isLegacySuperAdminMenu(menu))
                .toList();
    }

    private Map<Long, List<AppMenuAction>> activeActionsByMenu() {
        return appMenuActionRepository.findByActiveTrueOrderByIdAsc().stream()
                .filter(action -> action.getAppMenu() != null)
                .collect(Collectors.groupingBy(action -> action.getAppMenu().getId()));
    }

    private List<AppMenuAction> actionsOf(Map<Long, List<AppMenuAction>> actionsByMenu, AppMenu menu) {
        return actionsByMenu.getOrDefault(menu.getId(), List.of());
    }

    private Map<Long, RoleMenuPermission> roleMenuPermissionsByMenu(Company company, RoleMaster role) {
        if (role == null) {
            return Map.of();
        }
        return roleMenuPermissionRepository.findByCompanyAndRole(company, role).stream()
                .filter(permission -> permission.getAppMenu() != null)
                .collect(Collectors.toMap(permission -> permission.getAppMenu().getId(), Function.identity(), (first, second) -> first));
    }

    // Action id ek hi menu ka hota hai, isliye key sirf action id.
    private Map<Long, RoleMenuActionPermission> roleActionPermissionsByAction(Company company, RoleMaster role) {
        if (role == null) {
            return Map.of();
        }
        return roleMenuActionPermissionRepository.findByCompanyAndRole(company, role).stream()
                .filter(permission -> permission.getAppMenuAction() != null)
                .collect(Collectors.toMap(permission -> permission.getAppMenuAction().getId(), Function.identity(), (first, second) -> first));
    }

    private Map<Long, UserPermission> userPermissionsByAction(Company company, User user) {
        return userPermissionRepository.findByCompanyAndUser(company, user).stream()
                .filter(permission -> permission.getAppMenuAction() != null)
                .collect(Collectors.toMap(permission -> permission.getAppMenuAction().getId(), Function.identity(), (first, second) -> first));
    }

    private MenuPermissionResponse toMenuResponse(AppMenu menu, boolean canView, List<AppMenuAction> actions, Function<AppMenuAction, Boolean> allowedResolver, boolean forceAllowed) {
        return toMenuResponse(menu, canView, actions, allowedResolver, forceAllowed, action -> null);
    }

    private MenuPermissionResponse toMenuResponse(AppMenu menu, boolean canView, List<AppMenuAction> actions, Function<AppMenuAction, Boolean> allowedResolver, boolean forceAllowed, Function<AppMenuAction, Boolean> overrideResolver) {
        return MenuPermissionResponse.builder()
                .id(menu.getId())
                .menuName(menu.getMenuName())
                .menuCode(menu.getMenuCode())
                .menuIcon(menu.getMenuIcon())
                .menuRoute(menu.getMenuRoute())
                .displayOrder(menu.getDisplayOrder())
                .parentMenuId(menu.getParentMenu() == null ? null : menu.getParentMenu().getId())
                .parentMenuCode(menu.getParentMenu() == null ? null : menu.getParentMenu().getMenuCode())
                .canView(canView)
                .actions(actions.stream()
                        .sorted(Comparator.comparing(AppMenuAction::getId))
                        .map(action -> ActionPermissionResponse.builder()
                                .id(action.getId())
                                .actionName(action.getActionName())
                                .actionCode(action.getActionCode())
                                .allowed(forceAllowed || Boolean.TRUE.equals(allowedResolver.apply(action)))
                                .overrideAllowed(overrideResolver.apply(action))
                                .build())
                        .toList())
                .build();
    }

    private Company requireOwnerCompany(String email) {
        return accessControlService.requireOwnerCompany(email);
    }

    private List<MenuPermissionResponse> toHierarchy(List<MenuPermissionResponse> menus) {
        Map<Long, MenuPermissionResponse> byId = new LinkedHashMap<>();
        Map<Long, List<MenuPermissionResponse>> childrenByParent = new LinkedHashMap<>();
        for (MenuPermissionResponse menu : menus) {
            MenuPermissionResponse copy = copyMenu(menu, new ArrayList<>());
            byId.put(copy.getId(), copy);
            if (copy.getParentMenuId() != null) {
                childrenByParent.computeIfAbsent(copy.getParentMenuId(), ignored -> new ArrayList<>()).add(copy);
            }
        }

        List<MenuPermissionResponse> roots = new ArrayList<>();
        for (MenuPermissionResponse menu : byId.values()) {
            List<MenuPermissionResponse> children = childrenByParent.getOrDefault(menu.getId(), List.of()).stream()
                    .sorted(Comparator.comparing((MenuPermissionResponse m) -> isSetupMenu(m) ? 1 : 0)
                            .thenComparing(MenuPermissionResponse::getDisplayOrder)
                            .thenComparing(MenuPermissionResponse::getId))
                    .toList();
            MenuPermissionResponse withChildren = copyMenu(menu, children);
            if (menu.getParentMenuId() == null) {
                if (withChildren.isCanView() || !children.isEmpty()) {
                    roots.add(withChildren);
                }
            }
        }
        return roots.stream()
                .sorted(Comparator.comparing((MenuPermissionResponse m) -> isSetupMenu(m) ? 1 : 0)
                        .thenComparing(MenuPermissionResponse::getDisplayOrder)
                        .thenComparing(MenuPermissionResponse::getId))
                .toList();
    }

    /**
     * Setup always renders last in the menu list, regardless of displayOrder.
     */
    private static boolean isSetupMenu(MenuPermissionResponse menu) {
        return menu != null && "SETUP".equals(menu.getMenuCode());
    }

    private MenuPermissionResponse copyMenu(MenuPermissionResponse menu, List<MenuPermissionResponse> children) {
        boolean canView = menu.isCanView() || !children.isEmpty();
        return MenuPermissionResponse.builder()
                .id(menu.getId())
                .menuName(menu.getMenuName())
                .menuCode(menu.getMenuCode())
                .menuIcon(menu.getMenuIcon())
                .menuRoute(menu.getMenuRoute())
                .displayOrder(menu.getDisplayOrder())
                .parentMenuId(menu.getParentMenuId())
                .parentMenuCode(menu.getParentMenuCode())
                .canView(canView)
                .actions(menu.getActions())
                .children(children)
                .build();
    }

    private String roleCode(User user) {
        return (user.getRole() == null ? RoleName.USER : user.getRole()).name();
    }

    private boolean isLegacySuperAdminMenu(AppMenu menu) {
        return menu != null && menu.getMenuCode() != null && menu.getMenuCode().startsWith(LEGACY_PLATFORM_ADMIN_MENU_CODE_PREFIX);
    }

    private AppMenu requireMenu(Map<Long, AppMenu> menus, Long menuId) {
        AppMenu menu = menus.get(menuId);
        if (menu == null) {
            throw new ResourceNotFoundException("Menu not found");
        }
        return menu;
    }

    private AppMenuAction requireAction(Map<Long, AppMenuAction> actions, Long actionId, AppMenu menu) {
        AppMenuAction action = actions.get(actionId);
        if (action == null || !action.getAppMenu().getId().equals(menu.getId())) {
            throw new ResourceNotFoundException("Menu action not found");
        }
        return action;
    }
}
