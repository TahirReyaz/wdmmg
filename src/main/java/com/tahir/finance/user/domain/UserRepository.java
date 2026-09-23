package com.tahir.finance.user.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("SELECT u FROM User u WHERE lower(u.email) = lower(:email) AND u.deletedAt IS NULL")
    Optional<User> findActiveByEmail(@Param("email") String email);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE lower(u.email) = lower(:email)")
    boolean emailTaken(@Param("email") String email);
}
