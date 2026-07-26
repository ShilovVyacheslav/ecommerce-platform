package com.shilov.ecommerce.userservice.service.impl;

import com.shilov.ecommerce.userservice.config.props.JwtProps;
import com.shilov.ecommerce.userservice.security.SecurityUser;
import com.shilov.ecommerce.userservice.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JwtServiceImpl implements JwtService {

    private final JwtProps jwtProps;

    @Override
    public String generateAccessToken(SecurityUser securityUser) {
        return buildAccessToken(securityUser, new HashMap<>());
    }

    @Override
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    @Override
    public boolean isTokenValid(String token, SecurityUser securityUser) {
        try {
            final String username = extractUsername(token);
            return (username.equals(securityUser.getUsername()) &&
                    !isTokenExpired(token) &&
                    areRolesConsistent(token, securityUser));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date(System.currentTimeMillis()));
    }

    private boolean areRolesConsistent(String token, UserDetails userDetails) {
        Set<String> tokenRoles = extractClaim(token, this::extractRoles);
        Set<String> userRoles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        return tokenRoles.equals(userRoles);
    }

    private Set<String> extractRoles(Map<String, Object> claims) {
        try {
            Object rolesClaim = claims.get("roles");
            if (rolesClaim instanceof List<?> list) {
                return list.stream()
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .map(String::toLowerCase)
                        .collect(Collectors.toSet());
            }
            return Collections.emptySet();
        } catch (Exception ex) {
            return Collections.emptySet();
        }
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private <T> T extractClaim(String token,
                               Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(jwtProps.getPublicKeyLocation())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String buildAccessToken(SecurityUser securityUser, Map<String, Object> claims) {
        claims.put("id", securityUser.getUser().getId());
        claims.put("roles", securityUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList())
        );
        return Jwts
                .builder()
                .claims(claims)
                .subject(securityUser.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtProps.getAccessExpiration() * 1000))
                .signWith(jwtProps.getPrivateKeyLocation(), Jwts.SIG.RS256)
                .compact();
    }

}