package com.farmflow.repository;

import com.farmflow.entity.Farm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FarmRepository extends JpaRepository<Farm, String>, JpaSpecificationExecutor<Farm> {
    List<Farm> findByStatus(String status);
}