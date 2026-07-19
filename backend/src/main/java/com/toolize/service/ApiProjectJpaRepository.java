package com.toolize.service;

import com.toolize.domain.ApiProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiProjectJpaRepository extends JpaRepository<ApiProjectEntity, String> {
}
