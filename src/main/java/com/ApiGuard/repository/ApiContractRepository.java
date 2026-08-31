package com.ApiGuard.repository;

import com.ApiGuard.model.ApiContract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiContractRepository extends JpaRepository<ApiContract, Long> {

    Optional<ApiContract> findByProjectIdAndVersion(String projectId, String version);

    Optional<ApiContract> findByProjectIdAndBaselineTrue(String projectId);

    List<ApiContract> findAllByProjectId(String projectId);
}
