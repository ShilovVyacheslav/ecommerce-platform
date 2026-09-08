package com.shilov.ecommerce.userservice.dto;

import com.shilov.ecommerce.userservice.enums.Role;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class UserRoleUpdateDto {
    @NotNull
    private Role role;
}
