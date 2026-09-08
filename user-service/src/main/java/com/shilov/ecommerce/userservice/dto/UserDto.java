package com.shilov.ecommerce.userservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shilov.ecommerce.userservice.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class UserDto {
    private Long id;
    private String fullname;
    private String username;
    private String email;
    private Role role;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    private Boolean active;
}
