package com.farmflow.repository;

import com.farmflow.entity.InventoryItem;
import com.farmflow.enums.InventoryCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryRepository extends JpaRepository<InventoryItem, String> {
    List<InventoryItem> findByFarmId(String farmId);
    List<InventoryItem> findByFarmIdAndCategory(String farmId, InventoryCategory category);
}


