package com.farmflow.repository;

import com.farmflow.domain.Farm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FarmRepository extends JpaRepository<Farm, String> {
    List<Farm> findByStatus(String status);
}
