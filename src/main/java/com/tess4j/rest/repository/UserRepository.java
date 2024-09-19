package com.tess4j.rest.repository;

import com.tess4j.rest.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByUserIdAndUserPassword(String userId, String userPassword);
}