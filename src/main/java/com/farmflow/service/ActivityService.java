package com.farmflow.service;

import com.farmflow.entity.FarmActivity;
import com.farmflow.dto.ActivityDtos.*;
import com.farmflow.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import com.farmflow.dto.PageResponse;
import com.farmflow.specification.ActivitySpecification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import com.farmflow.event.ActivityCompletedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxService outboxService;
    private final com.farmflow.config.FarmSecurityEvaluator farmSecurity;

    public List<FarmActivity> getActivities(String plantationId, String farmId, Boolean completed) {
        String effectiveFarmId = farmId;
        if (!farmSecurity.isAdmin() && effectiveFarmId == null && plantationId == null) {
            effectiveFarmId = farmSecurity.getCurrentUserFarmId();
        }
        if (plantationId != null && completed != null) {
            return activityRepository.findByPlantationIdAndCompleted(plantationId, completed);
        } else if (plantationId != null) {
            return activityRepository.findByPlantationId(plantationId);
        } else if (effectiveFarmId != null) {
            return activityRepository.findByFarmId(effectiveFarmId);
        }
        if (farmSecurity.isAdmin()) {
            return activityRepository.findAll();
        }
        return List.of();
    }

    public FarmActivity getActivityById(String id) {
        FarmActivity activity = activityRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Atividade não encontrada"));
        farmSecurity.validateTenantAccess(activity.getFarmId());
        return activity;
    }

    @Transactional
    public FarmActivity createActivity(CreateActivityRequest request) {
        farmSecurity.validateTenantAccess(request.farmId());
        FarmActivity activity = FarmActivity.builder()
            .plantationId(request.plantationId())
            .farmId(request.farmId())
            .title(request.title())
            .type(request.type())
            .date(request.date())
            .priority(request.priority() != null ? request.priority() : "medium")
            .notes(request.notes())
            .costEstimate(request.costEstimate())
            .completed(false)
            .build();

        return activityRepository.save(activity);
    }

    @Transactional
    public FarmActivity updateActivity(String id, UpdateActivityRequest request) {
        FarmActivity activity = getActivityById(id);

        activity.updateDetails(
            request.title(),
            request.type(),
            request.date(),
            request.priority(),
            request.notes(),
            request.costEstimate()
        );
        if (request.completed() != null) {
            if (Boolean.TRUE.equals(request.completed())) {
                activity.complete();
            } else {
                activity.reopen();
            }
        }

        FarmActivity saved = activityRepository.save(activity);
        if (Boolean.TRUE.equals(saved.getCompleted())) {
            ActivityCompletedEvent event = new ActivityCompletedEvent(saved);
            outboxService.recordEvent("ActivityCompleted", event);
            eventPublisher.publishEvent(event);
        }
        return saved;
    }

    @Transactional
    public FarmActivity toggleStatus(String id, boolean completed) {
        FarmActivity activity = getActivityById(id);
        if (completed) {
            activity.complete();
        } else {
            activity.reopen();
        }
        FarmActivity saved = activityRepository.save(activity);
        if (completed) {
            ActivityCompletedEvent event = new ActivityCompletedEvent(saved);
            outboxService.recordEvent("ActivityCompleted", event);
            eventPublisher.publishEvent(event);
        }
        return saved;
    }

    public PageResponse<FarmActivity> getActivitiesPaged(String farmId, String plantationId, String type, Boolean completed, Pageable pageable) {
        Specification<FarmActivity> spec = Specification.allOf(
            ActivitySpecification.hasFarmId(farmId),
            ActivitySpecification.hasPlantationId(plantationId),
            ActivitySpecification.hasType(type),
            ActivitySpecification.isCompleted(completed)
        );
        return PageResponse.from(activityRepository.findAll(spec, pageable));
    }

    @Transactional
    public void deleteActivity(String id) {
        FarmActivity activity = getActivityById(id);
        activityRepository.delete(activity);
    }
}