package com.farmflow.repository;

import com.farmflow.entity.SagaAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SagaAuditLogRepository extends JpaRepository<SagaAuditLog, String> {
    List<SagaAuditLog> findByFarmIdAndActivityId(String farmId, String activityId);
}
