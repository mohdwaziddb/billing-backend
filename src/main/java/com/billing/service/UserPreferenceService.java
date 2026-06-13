package com.billing.service;

import com.billing.dto.user.ColumnPreferenceRequest;
import com.billing.dto.user.ColumnPreferenceResponse;
import com.billing.dto.user.UserPreferenceRequest;
import com.billing.dto.user.UserPreferenceResponse;
import com.billing.entity.User;
import com.billing.entity.UserPreference;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.repository.UserPreferenceRepository;
import com.billing.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public UserPreferenceResponse getPreferences(String email) {
        User user = requireUser(email);
        UserPreference preference = userPreferenceRepository.findByUser(user)
                .orElseGet(() -> UserPreference.builder().user(user).darkModeEnabled(false).build());
        return toResponse(preference);
    }

    @Transactional
    public UserPreferenceResponse updatePreferences(String email, UserPreferenceRequest request) {
        User user = requireUser(email);
        UserPreference preference = userPreferenceRepository.findByUser(user)
                .orElseGet(() -> UserPreference.builder().user(user).build());
        preference.setDarkModeEnabled(request.isDarkModeEnabled());
        return toResponse(userPreferenceRepository.save(preference));
    }

    @Transactional(readOnly = true)
    public ColumnPreferenceResponse getColumnPreference(String email, String tableName) {
        User user = requireUser(email);
        UserPreference preference = userPreferenceRepository.findByUser(user)
                .orElseGet(() -> UserPreference.builder().user(user).darkModeEnabled(false).build());
        String normalizedTableName = normalizeTableName(tableName);
        Map<String, List<String>> preferences = readColumnPreferences(preference);
        return toColumnPreferenceResponse(preference, user, normalizedTableName,
                preferences.getOrDefault(normalizedTableName, List.of()));
    }

    @Transactional
    public ColumnPreferenceResponse updateColumnPreference(String email, String tableName, ColumnPreferenceRequest request) {
        User user = requireUser(email);
        UserPreference preference = userPreferenceRepository.findByUser(user)
                .orElseGet(() -> UserPreference.builder().user(user).darkModeEnabled(false).build());
        String normalizedTableName = normalizeTableName(tableName);
        List<String> normalizedColumns = normalizeVisibleColumns(request == null ? null : request.getVisibleColumns());

        Map<String, List<String>> preferences = new LinkedHashMap<>(readColumnPreferences(preference));
        preferences.put(normalizedTableName, normalizedColumns);
        preference.setColumnPreferences(writeColumnPreferences(preferences));

        UserPreference saved = userPreferenceRepository.save(preference);
        return toColumnPreferenceResponse(saved, user, normalizedTableName, normalizedColumns);
    }

    private User requireUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private UserPreferenceResponse toResponse(UserPreference preference) {
        return UserPreferenceResponse.builder()
                .darkModeEnabled(preference.isDarkModeEnabled())
                .build();
    }

    private ColumnPreferenceResponse toColumnPreferenceResponse(UserPreference preference,
                                                                User user,
                                                                String tableName,
                                                                List<String> visibleColumns) {
        LocalDateTime createdOn = preference.getCreatedAt();
        LocalDateTime updatedOn = preference.getUpdatedAt();
        return ColumnPreferenceResponse.builder()
                .id(preference.getId())
                .companyId(user.getCompany() == null ? null : user.getCompany().getId())
                .userId(user.getId())
                .tableName(tableName)
                .visibleColumns(visibleColumns)
                .createdOn(createdOn)
                .updatedOn(updatedOn)
                .build();
    }

    private String normalizeTableName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new BadRequestException("Table name is required");
        }
        return tableName.trim().toUpperCase(Locale.ENGLISH);
    }

    private List<String> normalizeVisibleColumns(List<String> visibleColumns) {
        if (visibleColumns == null || visibleColumns.isEmpty()) {
            throw new BadRequestException("At least one visible column is required");
        }
        List<String> normalized = visibleColumns.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new BadRequestException("At least one visible column is required");
        }
        return normalized;
    }

    private Map<String, List<String>> readColumnPreferences(UserPreference preference) {
        if (preference.getColumnPreferences() == null || preference.getColumnPreferences().isBlank()) {
            return Map.of();
        }
        try {
            Map<String, List<String>> raw = objectMapper.readValue(preference.getColumnPreferences(),
                    new TypeReference<Map<String, List<String>>>() {
                    });
            Map<String, List<String>> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : raw.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    continue;
                }
                List<String> columns = entry.getValue() == null
                        ? List.of()
                        : entry.getValue().stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .collect(java.util.stream.Collectors.collectingAndThen(
                                java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                                List::copyOf));
                normalized.put(entry.getKey().trim().toUpperCase(Locale.ENGLISH), columns);
            }
            return normalized;
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Stored column preferences are invalid");
        }
    }

    private String writeColumnPreferences(Map<String, List<String>> preferences) {
        try {
            return objectMapper.writeValueAsString(preferences);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Unable to store column preferences");
        }
    }
}
