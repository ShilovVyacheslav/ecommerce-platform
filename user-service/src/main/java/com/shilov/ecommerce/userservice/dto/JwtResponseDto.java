package com.shilov.ecommerce.userservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JwtResponseDto {
    private String token;
    @Builder.Default
    private final String tokenType = "Bearer";
    private long expiresIn;
}
