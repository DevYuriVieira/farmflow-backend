package com.farmflow.service;

import com.farmflow.config.CorrelationIdFilter;
import com.farmflow.config.FarmSecurityEvaluator;
import com.farmflow.dto.InventoryDtos.*;
import com.farmflow.entity.InventoryItem;
import com.farmflow.enums.InventoryCategory;
import com.farmflow.event.cloudevents.CloudEvent;
import com.farmflow.event.cloudevents.InputReservedData;
import com.farmflow.exceptions.BusinessException;
import com.farmflow.exceptions.ResourceNotFoundException;
import com.farmflow.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final FarmSecurityEvaluator farmSecurity;
    private final OutboxService outboxService;

    public List<InventoryItem> getAllItems() {
        if (farmSecurity.isAdmin()) {
            return inventoryRepository.findAll();
        }
        String farmId = farmSecurity.getCurrentUserFarmId();
        if (farmId == null) {
            return List.of();
        }
        return inventoryRepository.findByFarmId(farmId);
    }

    public List<InventoryItem> getItemsByFarm(String farmId, InventoryCategory category) {
        farmSecurity.validateTenantAccess(farmId);
        if (category != null) {
            return inventoryRepository.findByFarmIdAndCategory(farmId, category);
        }
        return inventoryRepository.findByFarmId(farmId);
    }

    public InventoryItem getItemById(String id) {
        InventoryItem item = inventoryRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Item de inventário não encontrado"));
        farmSecurity.validateTenantAccess(item.getFarmId());
        return item;
    }

    @Transactional
    public InventoryItem createItem(CreateInventoryRequest request) {
        farmSecurity.validateTenantAccess(request.farmId());
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

    @Transactional
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

    @Transactional
    public InventoryItem deductStock(String id, Double amount) {
        if (amount == null || amount <= 0.0) {
            throw new BusinessException("Deduction amount must be positive.");
        }
        getItemById(id);
        int updated = inventoryRepository.deductAtomically(id, amount, java.time.Instant.now());
        if (updated == 0) {
            throw new BusinessException("Insufficient inventory to deduct " + amount + " units.");
        }
        return inventoryRepository.findById(id).orElseThrow();
    }

    @Transactional
    public InventoryItem replenishStock(String id, Double amount) {
        if (amount == null || amount <= 0.0) {
            throw new BusinessException("Replenishment amount must be positive.");
        }
        getItemById(id);
        inventoryRepository.replenishAtomically(id, amount, java.time.Instant.now());
        return inventoryRepository.findById(id).orElseThrow();
    }

    @Transactional
    public InventoryItem reserveStock(String id, String farmId, String activityId, Double amount) {
        if (amount == null || amount <= 0.0) {
            throw new BusinessException("Reservation amount must be positive.");
        }
        if (farmId == null || farmId.isBlank()) {
            throw new BusinessException("Farm ID cannot be blank.");
        }
        if (activityId == null || activityId.isBlank()) {
            throw new BusinessException("Activity ID cannot be blank.");
        }

        farmSecurity.validateTenantAccess(farmId);
        InventoryItem item = getItemById(id);
        if (!farmId.equals(item.getFarmId())) {
            throw new BusinessException("Item does not belong to specified farm.");
        }

        int updated = inventoryRepository.deductAtomically(id, amount, Instant.now());
        if (updated == 0) {
            throw new BusinessException("Insufficient inventory to reserve " + amount + " units.");
        }

        String correlationId = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        InputReservedData data = new InputReservedData(
                UUID.randomUUID().toString(),
                farmId,
                activityId,
                id,
                amount,
                item.getUnit(),
                Instant.now()
        );

        CloudEvent<InputReservedData> cloudEvent = CloudEvent.of(
                "https://farmflow.com/supply-service",
                "com.farmflow.supply.input.reserved.v1",
                farmId,
                correlationId,
                data
        );

        outboxService.recordCloudEvent(cloudEvent);

        return inventoryRepository.findById(id).orElseThrow();
    }

    @Transactional
    public void deleteItem(String id) {
        InventoryItem item = getItemById(id);
        inventoryRepository.delete(item);
    }
}
