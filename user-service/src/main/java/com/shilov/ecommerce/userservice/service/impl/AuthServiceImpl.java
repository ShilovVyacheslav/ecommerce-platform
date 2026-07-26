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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

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

        return buildLoginResponse(jwtService.generateAccessToken(securityUser));
    }

    @Override
    public JwtResponseDto refresh(Jwt jwt) {

        String username = jwt.getSubject();

        SecurityUser securityUser = (SecurityUser) securityService.loadUserByUsername(username);

        return buildLoginResponse(jwtService.generateAccessToken(securityUser));
    }

    private Authentication authenticate(LoginRequestDto dto) {
        try {
            return authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.getUsername(), dto.getPassword())
            );
        } catch (BadCredentialsException e) {
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

