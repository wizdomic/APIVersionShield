package com.ApiGuard.repository;

import com.ApiGuard.model.ApiContract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiContractRepository extends JpaRepository<ApiContract,Long> {
    Optional<ApiContract> findByVersion(String version);
}
