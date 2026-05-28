package com.juiceplatform.repository;

import com.juiceplatform.entity.AdminCredentials;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminCredentialsRepository extends JpaRepository<AdminCredentials, UUID> {

    Optional<AdminCredentials> findByUserId(UUID userId);
}
