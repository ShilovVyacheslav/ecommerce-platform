package com.shilov.ecommerce.userservice.repository;

import com.shilov.ecommerce.userservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Boolean existsByUsernameIgnoreCase(String username);

    Boolean existsByEmail(String email);

}
