package com.shilov.ecommerce.userservice.service;

import com.shilov.ecommerce.userservice.dto.UserDto;
import com.shilov.ecommerce.userservice.dto.UserRegisterDto;
import com.shilov.ecommerce.userservice.dto.UserRoleUpdateDto;
import com.shilov.ecommerce.userservice.dto.UserUpdateDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    UserDto createUser(UserRegisterDto userRegisterDto);

    Page<UserDto> getUsers(Pageable pageable);

    UserDto getUser(Long id);

    UserDto updateUser(Long id, UserUpdateDto userUpdateDto);

    UserDto changeUserRole(Long id, UserRoleUpdateDto userRoleUpdateDto);

}
