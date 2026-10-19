package com.farmflow.service;

import com.farmflow.common.exception.ResourceNotFoundException;
import com.farmflow.domain.InventoryItem;
import com.farmflow.domain.enums.InventoryCategory;
import com.farmflow.dto.inventory.InventoryDtos.*;
import com.farmflow.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public List<InventoryItem> getItemsByFarm(String farmId, InventoryCategory category) {
        if (category != null) {
            return inventoryRepository.findByFarmIdAndCategory(farmId, category);
        }
        return inventoryRepository.findByFarmId(farmId);
    }

    public InventoryItem getItemById(String id) {
        return inventoryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Item de inventário não encontrado"));
    }

    public InventoryItem createItem(CreateInventoryRequest request) {
        InventoryItem item = InventoryItem.builder()
            .farmId(request.farmId())
            .name(request.name())
            .category(request.category())
            .quantity(request.quantity())
            .unit(request.unit())
            .minimumThreshold(request.minimumThreshold())
            .unitPrice(request.unitPrice())
            .location(request.location())
            .build();

        return inventoryRepository.save(item);
    }

    public InventoryItem updateItem(String id, UpdateInventoryRequest request) {
        InventoryItem item = getItemById(id);

        if (request.name() != null) item.setName(request.name());
        if (request.category() != null) item.setCategory(request.category());
        if (request.quantity() != null) item.setQuantity(request.quantity());
        if (request.unit() != null) item.setUnit(request.unit());
        if (request.minimumThreshold() != null) item.setMinimumThreshold(request.minimumThreshold());
        if (request.unitPrice() != null) item.setUnitPrice(request.unitPrice());
        if (request.location() != null) item.setLocation(request.location());
        item.setUpdatedAt(Instant.now());

        return inventoryRepository.save(item);
    }

    public void deleteItem(String id) {
        InventoryItem item = getItemById(id);
        inventoryRepository.delete(item);
    }
}