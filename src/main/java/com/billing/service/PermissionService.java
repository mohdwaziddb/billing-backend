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
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
        return has(authentication.getName(), menuCode, actionCode);
    }

    @Transactional(readOnly = true)
    public boolean has(String email, String menuCode, String actionCode) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return hasPermission(user.getId(), user.getCompany().getId(), menuCode, actionCode);
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(Long userId, Long companyId, String menuCode, String actionCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Company company = user.getCompany();
        if (company == null || !company.getId().equals(companyId)) {
            return false;
        }

        AppMenu menu = appMenuRepository.findByMenuCode(menuCode).orElse(null);
        if (menu == null || !menu.isActive()) {
            return false;
        }
        AppMenuAction action = appMenuActionRepository.findByAppMenuAndActionCode(menu, actionCode).orElse(null);
        if (action == null || !action.isActive()) {
            return false;
        }
        if (accessControlService.isCompanyOwner(user)) {
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
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (accessControlService.isCompanyOwner(user)) {
            return PermissionMatrixResponse.builder()
                    .roleCode(roleCode(user))
                    .userId(user.getId())
                    .menus(toHierarchy(allMenus(true, user, null, user.getCompany(), true)))
                    .build();
        }
        RoleMaster role = roleMasterRepository.findByRoleCode(roleCode(user))
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
        return PermissionMatrixResponse.builder()
                .roleCode(roleCode(user))
                .userId(user.getId())
                .menus(toHierarchy(allMenus(false, user, role, user.getCompany(), true)))
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
        requireSameCompany(company, user);
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
        for (MenuPermissionRequest menuRequest : request.getMenus()) {
            AppMenu menu = requireMenu(menus, menuRequest.getMenuId());
            RoleMenuPermission menuPermission = roleMenuPermissionRepository.findByCompanyAndRoleAndAppMenu(company, role, menu)
                    .orElse(RoleMenuPermission.builder().company(company).role(role).appMenu(menu).build());
            menuPermission.setCanView(menuRequest.isCanView());
            roleMenuPermissionRepository.save(menuPermission);
            for (ActionPermissionRequest actionRequest : menuRequest.getActions()) {
                AppMenuAction action = requireAction(actions, actionRequest.getActionId(), menu);
                RoleMenuActionPermission actionPermission = roleMenuActionPermissionRepository.findByCompanyAndRoleAndAppMenuAndAppMenuAction(company, role, menu, action)
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
        requireSameCompany(company, user);
        if (accessControlService.isCompanyOwner(user)) {
            return userMatrix(ownerEmail, user.getId());
        }
        Map<Long, AppMenu> menus = appMenuRepository.findAll().stream().collect(Collectors.toMap(AppMenu::getId, Function.identity()));
        Map<Long, AppMenuAction> actions = appMenuActionRepository.findAll().stream().collect(Collectors.toMap(AppMenuAction::getId, Function.identity()));
        for (MenuPermissionRequest menuRequest : request.getMenus()) {
            AppMenu menu = requireMenu(menus, menuRequest.getMenuId());
            for (ActionPermissionRequest actionRequest : menuRequest.getActions()) {
                AppMenuAction action = requireAction(actions, actionRequest.getActionId(), menu);
                if (actionRequest.getOverrideAllowed() == null) {
                    userPermissionRepository.findByCompanyAndUserAndAppMenuAndAppMenuAction(company, user, menu, action)
                            .ifPresent(userPermissionRepository::delete);
                    continue;
                }
                UserPermission permission = userPermissionRepository.findByCompanyAndUserAndAppMenuAndAppMenuAction(company, user, menu, action)
                        .orElse(UserPermission.builder().company(company).user(user).appMenu(menu).appMenuAction(action).build());
                permission.setAllowed(actionRequest.getOverrideAllowed());
                userPermissionRepository.save(permission);
            }
        }
        return userMatrix(ownerEmail, user.getId());
    }

    private List<MenuPermissionResponse> allMenus(boolean allowAll, User user, RoleMaster role, Company company, boolean onlyVisible) {
        if (allowAll) {
            return appMenuRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                    .map(menu -> toMenuResponse(menu, true, appMenuActionRepository.findByAppMenuAndActiveTrueOrderByIdAsc(menu), null, true))
                    .toList();
        }
        return appMenuRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                .map(menu -> effectiveMenuResponse(company, user, role, menu))
                .filter(menu -> !onlyVisible || menu.isCanView())
                .toList();
    }

    private List<MenuPermissionResponse> roleMenus(Company company, RoleMaster role) {
        return appMenuRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                .map(menu -> {
                    boolean canView = "OWNER".equals(role.getRoleCode()) || roleMenuPermissionRepository.findByCompanyAndRoleAndAppMenu(company, role, menu).map(RoleMenuPermission::isCanView).orElse(false);
                    List<AppMenuAction> actions = appMenuActionRepository.findByAppMenuAndActiveTrueOrderByIdAsc(menu);
                    return toMenuResponse(menu, canView, actions, action -> "OWNER".equals(role.getRoleCode()) || roleMenuActionPermissionRepository.findByCompanyAndRoleAndAppMenuAndAppMenuAction(company, role, menu, action).map(RoleMenuActionPermission::isAllowed).orElse(false), false);
                })
                .toList();
    }

    private List<MenuPermissionResponse> userMenus(Company company, User user, RoleMaster role) {
        return appMenuRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                .map(menu -> effectiveMenuResponse(company, user, role, menu))
                .toList();
    }

    private MenuPermissionResponse effectiveMenuResponse(Company company, User user, RoleMaster role, AppMenu menu) {
        boolean canView = roleMenuPermissionRepository.findByCompanyAndRoleAndAppMenu(company, role, menu).map(RoleMenuPermission::isCanView).orElse(false);
        List<AppMenuAction> actions = appMenuActionRepository.findByAppMenuAndActiveTrueOrderByIdAsc(menu);
        Optional<AppMenuAction> viewAction = actions.stream().filter(action -> "VIEW".equals(action.getActionCode())).findFirst();
        if (viewAction.isPresent()) {
            Optional<UserPermission> override = userPermissionRepository.findByCompanyAndUserAndAppMenuAndAppMenuAction(company, user, menu, viewAction.get());
            if (override.isPresent()) {
                canView = override.get().isAllowed();
            }
        }
        return toMenuResponse(menu, canView, actions, action -> {
            Optional<UserPermission> override = userPermissionRepository.findByCompanyAndUserAndAppMenuAndAppMenuAction(company, user, menu, action);
            return override.map(UserPermission::isAllowed)
                    .orElseGet(() -> roleMenuActionPermissionRepository.findByCompanyAndRoleAndAppMenuAndAppMenuAction(company, role, menu, action).map(RoleMenuActionPermission::isAllowed).orElse(false));
        }, false, action -> userPermissionRepository.findByCompanyAndUserAndAppMenuAndAppMenuAction(company, user, menu, action).map(UserPermission::isAllowed).orElse(null));
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
                    .sorted(Comparator.comparing(MenuPermissionResponse::getDisplayOrder).thenComparing(MenuPermissionResponse::getId))
                    .toList();
            MenuPermissionResponse withChildren = copyMenu(menu, children);
            if (menu.getParentMenuId() == null) {
                if (withChildren.isCanView() || !children.isEmpty()) {
                    roots.add(withChildren);
                }
            }
        }
        return roots.stream()
                .sorted(Comparator.comparing(MenuPermissionResponse::getDisplayOrder).thenComparing(MenuPermissionResponse::getId))
                .toList();
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

    private void requireSameCompany(Company company, User targetUser) {
        if (targetUser.getCompany() == null || !targetUser.getCompany().getId().equals(company.getId())) {
            throw new ResourceNotFoundException("User not found");
        }
    }

    private String roleCode(User user) {
        return (user.getRole() == null ? RoleName.USER : user.getRole()).name();
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
