package com.shilov.ecommerce.userservice.mapper;

import com.shilov.ecommerce.userservice.dto.UserDto;
import com.shilov.ecommerce.userservice.dto.UserRegisterDto;
import com.shilov.ecommerce.userservice.dto.UserUpdateDto;
import com.shilov.ecommerce.userservice.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueMappingStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        componentModel = "spring",
        nullValueMappingStrategy = NullValueMappingStrategy.RETURN_NULL,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface UserMapper {

    UserDto toUserDto(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "role", constant = "USER")
    @Mapping(target = "password", source = "encodedPassword")
    User toEntity(UserRegisterDto userRegisterDto, String encodedPassword);

    void fromUpdateDto(UserUpdateDto userUpdateDto, @MappingTarget User user);

}
