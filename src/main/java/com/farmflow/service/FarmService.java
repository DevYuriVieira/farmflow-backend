package com.farmflow.service;

import com.farmflow.entity.Farm;
import com.farmflow.dto.FarmDtos.*;
import com.farmflow.repository.FarmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import com.farmflow.dto.PageResponse;
import com.farmflow.specification.FarmSpecification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FarmService {

    private final FarmRepository farmRepository;
    private final com.farmflow.config.FarmSecurityEvaluator farmSecurity;
    private final com.farmflow.repository.UserRepository userRepository;

    public List<Farm> getAllFarms() {
        if (farmSecurity.isAdmin()) {
            return farmRepository.findAll();
        }
        String farmId = farmSecurity.getCurrentUserFarmId();
        if (farmId != null) {
            return farmRepository.findById(farmId).map(List::of).orElse(List.of());
        }
        return farmRepository.findAll();
    }

    public Farm getFarmById(String id) {
        farmSecurity.validateTenantAccess(id);
        return farmRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fazenda não encontrada"));
    }

    @Transactional
    public Farm createFarm(CreateFarmRequest request) {
        Farm farm = Farm.builder()
            .name(request.name())
            .location(request.location())
            .totalAreaHectares(request.totalAreaHectares())
            .primaryCrop(request.primaryCrop())
            .ownerName(request.ownerName())
            .contactPhone(request.contactPhone())
            .status("active")
            .build();

        Farm saved = farmRepository.save(farm);

        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.farmflow.entity.User user) {
            if (user.getFarmId() == null) {
                user.setFarmId(saved.getId());
                userRepository.save(user);
            }
        }

        return saved;
    }

    @Transactional
    public Farm updateFarm(String id, UpdateFarmRequest request) {
        Farm farm = getFarmById(id);

        if (request.name() != null) farm.setName(request.name());
        if (request.location() != null) farm.setLocation(request.location());
        if (request.totalAreaHectares() != null) farm.setTotalAreaHectares(request.totalAreaHectares());
        if (request.primaryCrop() != null) farm.setPrimaryCrop(request.primaryCrop());
        if (request.ownerName() != null) farm.setOwnerName(request.ownerName());
        if (request.contactPhone() != null) farm.setContactPhone(request.contactPhone());
        if (request.status() != null) farm.setStatus(request.status());

        return farmRepository.save(farm);
    }

    public PageResponse<Farm> getFarmsPaged(String name, String location, String status, Pageable pageable) {
        Specification<Farm> spec = Specification.allOf(
            FarmSpecification.nameContains(name),
            FarmSpecification.locationContains(location),
            FarmSpecification.hasStatus(status)
        );
        return PageResponse.from(farmRepository.findAll(spec, pageable));
    }

    @Transactional
    public void deleteFarm(String id) {
        Farm farm = getFarmById(id);
        farmRepository.delete(farm);
    }
}


