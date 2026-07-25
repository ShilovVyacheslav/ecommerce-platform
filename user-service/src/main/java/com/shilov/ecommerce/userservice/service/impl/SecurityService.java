package com.shilov.ecommerce.userservice.service.impl;

import com.shilov.ecommerce.userservice.entity.User;
import com.shilov.ecommerce.userservice.exception.AuthException;
import com.shilov.ecommerce.userservice.exception.UserException;
import com.shilov.ecommerce.userservice.repository.UserRepository;
import com.shilov.ecommerce.userservice.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SecurityService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UserException, AuthException {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(String.format("User '%s' not found", username)));

        if (!user.getActive()) {
            throw AuthException.accessDenied();
        }

        return SecurityUser.builder().user(user).build();
    }

}
