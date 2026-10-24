package com.shilov.ecommerce.userservice.service.impl;

import com.shilov.ecommerce.userservice.config.props.JwtProps;
import com.shilov.ecommerce.userservice.dto.JwtResponseDto;
import com.shilov.ecommerce.userservice.dto.LoginRequestDto;
import com.shilov.ecommerce.userservice.exception.AuthException;
import com.shilov.ecommerce.userservice.security.SecurityUser;
import com.shilov.ecommerce.userservice.service.AuthService;
import com.shilov.ecommerce.userservice.service.JwtService;
import com.shilov.ecommerce.userservice.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import static net.logstash.logback.argument.StructuredArguments.v;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final JwtService jwtService;
    private final SecurityService securityService;
    private final AuthenticationManager authenticationManager;
    private final JwtProps jwtProps;

    @Override
    public JwtResponseDto login(String authorization) {

        LoginRequestDto loginRequestDto = AuthUtil.base64ToLoginRequestDto(authorization);

        SecurityUser securityUser = (SecurityUser) authenticate(loginRequestDto).getPrincipal();
        log.info("User {} logged in", v("targetUserId", securityUser.getUser().getId()));

        return buildLoginResponse(jwtService.generateAccessToken(securityUser));
    }

    @Override
    public JwtResponseDto refresh(Jwt jwt) {

        String username = jwt.getSubject();

        SecurityUser securityUser = (SecurityUser) securityService.loadUserByUsername(username);
        log.debug("Access token refreshed for user {}", v("targetUserId", securityUser.getUser().getId()));

        return buildLoginResponse(jwtService.generateAccessToken(securityUser));
    }

    private Authentication authenticate(LoginRequestDto dto) {
        try {
            return authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.getUsername(), dto.getPassword())
            );
        } catch (BadCredentialsException e) {
            log.warn("Login failed: invalid credentials");
            throw AuthException.invalidCredentials();
        }
    }

    private JwtResponseDto buildLoginResponse(String accessToken) {
        return JwtResponseDto.builder()
                .token(accessToken)
                .expiresIn(jwtProps.getAccessExpiration())
                .build();
    }

}

