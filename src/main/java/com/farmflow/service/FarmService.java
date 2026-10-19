package com.farmflow.service;

import com.farmflow.domain.Farm;
import com.farmflow.dto.FarmDtos.*;
import com.farmflow.repository.FarmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FarmService {

    private final FarmRepository farmRepository;

    public List<Farm> getAllFarms() {
        return farmRepository.findAll();
    }

    public Farm getFarmById(String id) {
        return farmRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fazenda não encontrada"));
    }

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

        return farmRepository.save(farm);
    }

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

    public void deleteFarm(String id) {
        Farm farm = getFarmById(id);
        farmRepository.delete(farm);
    }
}
