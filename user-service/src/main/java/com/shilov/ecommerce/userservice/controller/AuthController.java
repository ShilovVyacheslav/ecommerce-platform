package com.shilov.ecommerce.userservice.controller;

import com.shilov.ecommerce.userservice.dto.JwtResponseDto;
import com.shilov.ecommerce.userservice.dto.UserDto;
import com.shilov.ecommerce.userservice.dto.UserRegisterDto;
import com.shilov.ecommerce.userservice.service.UserService;
import com.shilov.ecommerce.userservice.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<UserDto> register(@Valid @RequestBody UserRegisterDto userRegisterDto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(userRegisterDto));
    }

    @PostMapping("/login")
    public ResponseEntity<JwtResponseDto> login(@RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(authService.login(authorization));
    }

    @PostMapping("/refresh")
    public ResponseEntity<JwtResponseDto> refresh(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(authService.refresh(jwt));
    }

}
