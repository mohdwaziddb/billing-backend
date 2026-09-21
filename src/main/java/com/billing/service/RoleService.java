package com.billing.service;

import com.billing.repository.RoleMasterRepository;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RoleService {

    private static final List<String> COMPANY_ROLE_CODES = List.of("OWNER", "ADMIN", "USER");

    private final RoleMasterRepository roleMasterRepository;

    @Transactional(readOnly = true)
    public List<String> listRoles() {
        List<String> companyRoleList = roleMasterRepository.findAll().stream()
                .map(roleMaster -> roleMaster.getRoleCode())
                .filter(COMPANY_ROLE_CODES::contains)
                .toList();
        return companyRoleList;
    }

    @Transactional(readOnly = true)
    public List<String> create(Map<String, Object> param) {
        Map<String, Object> sanitizedParam = param;
        if (sanitizedParam == null) {
            sanitizedParam = Map.of();
        }
        String searchFilter = DataTypeUtility.stringValue(sanitizedParam.get("search"));
        if (searchFilter.length() > 0) {
            String normalizedSearch = searchFilter.trim().toLowerCase();
            if (normalizedSearch.length() > 0) {
                List<String> filteredRoleList = roleMasterRepository.findAll().stream()
                        .map(roleMaster -> roleMaster.getRoleCode())
                        .filter(COMPANY_ROLE_CODES::contains)
                        .filter(roleCode -> roleCode.toLowerCase().contains(normalizedSearch))
                        .toList();
                return filteredRoleList;
            }
        }
        List<String> companyRoleList = roleMasterRepository.findAll().stream()
                .map(roleMaster -> roleMaster.getRoleCode())
                .filter(COMPANY_ROLE_CODES::contains)
                .toList();
        return companyRoleList;
    }

    @Transactional(readOnly = true)
    public List<String> listRoles(Map<String, Object> param) {
        return create(param);
    }
}
