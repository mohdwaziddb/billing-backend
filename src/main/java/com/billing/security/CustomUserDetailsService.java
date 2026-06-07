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

    private java.util.Optional<User> findByLoginIdentifier(String username) {
        String normalized = username == null ? null : username.trim();
        if (normalized != null && normalized.contains("@")) {
            return userRepository.findByEmailIgnoreCase(normalized);
        }
        return userRepository.findByMobileNumber(normalized);
    }
}
