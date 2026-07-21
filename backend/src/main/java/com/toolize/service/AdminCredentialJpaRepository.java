package com.toolize.service;

import com.toolize.domain.AdminCredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminCredentialJpaRepository extends JpaRepository<AdminCredentialEntity, String> {
}
