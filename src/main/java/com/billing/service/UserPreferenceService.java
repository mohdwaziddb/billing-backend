package com.billing.service;

import com.billing.dto.user.UserPreferenceRequest;
import com.billing.dto.user.UserPreferenceResponse;
import com.billing.entity.User;
import com.billing.entity.UserPreference;
import com.billing.exception.ResourceNotFoundException;
import com.billing.repository.UserPreferenceRepository;
import com.billing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;

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

    private User requireUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private UserPreferenceResponse toResponse(UserPreference preference) {
        return UserPreferenceResponse.builder()
                .darkModeEnabled(preference.isDarkModeEnabled())
                .build();
    }
}
