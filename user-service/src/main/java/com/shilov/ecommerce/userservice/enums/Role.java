package com.shilov.ecommerce.userservice.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Role {
    USER,
    ADMIN,
    SUPER_ADMIN;

    @Override
    public String toString() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static Role fromString(String name) {
        return Role.valueOf(name.toUpperCase());
    }

    @JsonValue
    public String toLower() {
        return name().toLowerCase();
    }

}
