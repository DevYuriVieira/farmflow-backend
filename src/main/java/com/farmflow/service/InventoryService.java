package com.farmflow.service;

import com.farmflow.exceptions.ResourceNotFoundException;
import com.farmflow.entity.InventoryItem;
import com.farmflow.enums.InventoryCategory;
import com.farmflow.dto.InventoryDtos.*;
import com.farmflow.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public List<InventoryItem> getAllItems() {
        return inventoryRepository.findAll();
    }

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

        item.updateDetails(
            request.name(),
            request.category(),
            request.unit(),
            request.minimumThreshold(),
            request.unitPrice(),
            request.location()
        );
        if (request.quantity() != null) {
            item.adjustStock(request.quantity());
        }

        return inventoryRepository.save(item);
    }

    public InventoryItem deductStock(String id, Double amount) {
        InventoryItem item = getItemById(id);
        item.deductStock(amount);
        return inventoryRepository.save(item);
    }

    public InventoryItem replenishStock(String id, Double amount) {
        InventoryItem item = getItemById(id);
        item.replenishStock(amount);
        return inventoryRepository.save(item);
    }

    public void deleteItem(String id) {
        InventoryItem item = getItemById(id);
        inventoryRepository.delete(item);
    }
}
