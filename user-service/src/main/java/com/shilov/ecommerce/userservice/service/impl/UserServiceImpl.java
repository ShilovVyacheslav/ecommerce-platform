package com.shilov.ecommerce.userservice.service.impl;

import com.shilov.ecommerce.userservice.dto.UserDto;
import com.shilov.ecommerce.userservice.dto.UserRegisterDto;
import com.shilov.ecommerce.userservice.dto.UserRoleUpdateDto;
import com.shilov.ecommerce.userservice.dto.UserUpdateDto;
import com.shilov.ecommerce.userservice.entity.User;
import com.shilov.ecommerce.userservice.exception.UserException;
import com.shilov.ecommerce.userservice.mapper.UserMapper;
import com.shilov.ecommerce.userservice.repository.UserRepository;
import com.shilov.ecommerce.userservice.service.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserDto createUser(UserRegisterDto userRegisterDto) {

        validateByUsername(userRegisterDto.getUsername());
        validateByEmail(userRegisterDto.getEmail());

        User user = userMapper.toEntity(userRegisterDto, passwordEncoder.encode(userRegisterDto.getPassword()));

        User savedUser = userRepository.save(user);

        return userMapper.toUserDto(savedUser);
    }

    @Override
    public Page<UserDto> getUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(userMapper::toUserDto);
    }

    @Override
    public UserDto getUserById(Long id) {
        return userMapper.toUserDto(userRepository.findById(id).orElseThrow(() -> UserException.userNotFound(id)));
    }

    @Override
    @Transactional
    public UserDto updateUser(Long id, UserUpdateDto userUpdateDto) {

        User user = userRepository.findById(id).orElseThrow(() -> UserException.userNotFound(id));

        validateUsernameUpdate(user, userUpdateDto);
        validateEmailUpdate(user, userUpdateDto);

        updateUserFields(user, userUpdateDto);

        return userMapper.toUserDto(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserDto changeUserRole(Long id, UserRoleUpdateDto userRoleUpdateDto) {

        User user = userRepository.findById(id).orElseThrow(() -> UserException.userNotFound(id));

        updateUserFields(user, UserUpdateDto.builder().role(userRoleUpdateDto.getRole()).build());

        return userMapper.toUserDto(userRepository.save(user));

    }

    private void validateByUsername(String username) {
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw UserException.userAlreadyExists(username);
        }
    }

    private void validateByEmail(String email) {
        if (email == null) {
            return;
        }
        if (userRepository.existsByEmail(email.toLowerCase())) {
            throw UserException.userAlreadyExists(email);
        }
    }

    private void validateUsernameUpdate(User user, UserUpdateDto userUpdateDto) {
        if (userUpdateDto.getUsername() != null && !userUpdateDto.getUsername().equalsIgnoreCase(user.getUsername())) {
            validateByUsername(userUpdateDto.getUsername());
        }
    }

    private void validateEmailUpdate(User user, UserUpdateDto userUpdateDto) {
        if (userUpdateDto.getEmail() != null && !userUpdateDto.getEmail().equalsIgnoreCase(user.getEmail())) {
            validateByEmail(userUpdateDto.getEmail());
        }
    }

    private void updateUserFields(User user, UserUpdateDto userUpdateDto) {
        userMapper.fromUpdateDto(userUpdateDto, user);
        if (userUpdateDto.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(userUpdateDto.getPassword()));
        }
    }

}
