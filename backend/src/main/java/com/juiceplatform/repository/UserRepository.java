package com.juiceplatform.repository;

import com.juiceplatform.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhone(String phone);

    Optional<User> findByGoogleId(String googleId);

    // Admin: list only customers (exclude ADMIN accounts)
    Page<User> findByRole(User.UserRole role, Pageable pageable);
}

