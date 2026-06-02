package com.billing.saas.service;

import com.billing.saas.dto.user.UserProfileResponse;
import com.billing.saas.entity.User;
import com.billing.saas.exception.ResourceNotFoundException;
import com.billing.saas.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return userMapper.toProfile(user);
    }
}
