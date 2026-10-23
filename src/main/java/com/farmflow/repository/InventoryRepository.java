package com.farmflow.repository;

import com.farmflow.entity.InventoryItem;
import com.farmflow.enums.InventoryCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryRepository extends JpaRepository<InventoryItem, String> {
    List<InventoryItem> findByFarmId(String farmId);
    List<InventoryItem> findByFarmIdAndCategory(String farmId, InventoryCategory category);

    @Modifying
    @Query("""
        UPDATE InventoryItem i
        SET i.quantity = i.quantity - :amt, i.version = i.version + 1, i.updatedAt = :now
        WHERE i.id = :id AND i.quantity >= :amt
        """)
    int deductAtomically(@Param("id") String id, @Param("amt") Double amt, @Param("now") java.time.Instant now);

    @Modifying
    @Query("""
        UPDATE InventoryItem i
        SET i.quantity = i.quantity + :amt, i.version = i.version + 1, i.updatedAt = :now
        WHERE i.id = :id
        """)
    int replenishAtomically(@Param("id") String id, @Param("amt") Double amt, @Param("now") java.time.Instant now);
}


