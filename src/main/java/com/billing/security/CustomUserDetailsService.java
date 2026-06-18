package com.billing.security;

import com.billing.entity.User;
import com.billing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = findByLoginIdentifier(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return new CustomUserDetails(user);
    }

    public UserDetails loadUserById(Long userId) throws UsernameNotFoundException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return new CustomUserDetails(user);
    }

    private java.util.Optional<User> findByLoginIdentifier(String username) {
        String normalized = username == null ? null : username.trim();
        java.util.List<User> candidates = new java.util.ArrayList<>();
        candidates.addAll(userRepository.findAllByUsernameIgnoreCase(normalized));
        candidates.addAll(userRepository.findAllByEmailIgnoreCase(normalized));
        candidates.addAll(userRepository.findAllByMobileNumber(normalized));
        java.util.Map<Long, User> byId = new java.util.LinkedHashMap<>();
        for (User candidate : candidates) {
            byId.putIfAbsent(candidate.getId(), candidate);
        }
        java.util.List<User> uniqueCandidates = new java.util.ArrayList<>(byId.values());
        return uniqueCandidates.size() == 1 ? uniqueCandidates.stream().findFirst() : java.util.Optional.empty();
    }
}
