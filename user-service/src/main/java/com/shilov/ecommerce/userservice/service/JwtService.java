package com.shilov.ecommerce.userservice.service;

import com.shilov.ecommerce.userservice.security.SecurityUser;

public interface JwtService {

    String generateAccessToken(SecurityUser securityUser);

    String extractUsername(String token);

    boolean isTokenValid(String token, SecurityUser securityUser);

}
