package com.shilov.ecommerce.userservice.service;

import com.shilov.ecommerce.userservice.dto.JwtResponseDto;
import org.springframework.security.oauth2.jwt.Jwt;

public interface AuthService {

    JwtResponseDto login(String authorization);

    JwtResponseDto refresh(Jwt jwt);

}
