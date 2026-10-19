package com.farmflow.repository;

import com.farmflow.entity.Machinery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MachineryRepository extends JpaRepository<Machinery, String> {
    List<Machinery> findByFarmId(String farmId);
    List<Machinery> findByFarmIdAndStatus(String farmId, String status);
}


