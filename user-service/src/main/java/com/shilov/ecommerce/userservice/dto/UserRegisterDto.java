package com.shilov.ecommerce.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import static com.shilov.ecommerce.userservice.validation.Patterns.FULLNAME_REGEXP;
import static com.shilov.ecommerce.userservice.validation.Patterns.PASSWORD_REGEXP;
import static com.shilov.ecommerce.userservice.validation.Patterns.USERNAME_REGEXP;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class UserRegisterDto {
    @NotBlank
    @Size(min = 2, max = 128)
    @Pattern(regexp = FULLNAME_REGEXP)
    private String fullname;

    @NotBlank
    @Size(min = 2, max = 32)
    @Pattern(regexp = USERNAME_REGEXP)
    private String username;

    @Email
    @NotBlank
    @Size(min = 6, max = 64)
    private String email;

    @NotBlank
    @Size(min = 8, max = 32)
    @Pattern(regexp = PASSWORD_REGEXP)
    private String password;
}
