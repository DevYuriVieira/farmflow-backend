package com.farmflow.repository;

import com.farmflow.domain.InventoryItem;
import com.farmflow.domain.enums.InventoryCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryRepository extends JpaRepository<InventoryItem, String> {
    List<InventoryItem> findByFarmId(String farmId);
    List<InventoryItem> findByFarmIdAndCategory(String farmId, InventoryCategory category);
}