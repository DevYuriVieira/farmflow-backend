package com.farmflow.service;

import com.farmflow.entity.FarmActivity;
import com.farmflow.dto.ActivityDtos.*;
import com.farmflow.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import com.farmflow.dto.PageResponse;
import com.farmflow.specification.ActivitySpecification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import com.farmflow.event.ActivityCompletedEvent;
import org.springframework.context.ApplicationEventPublisher;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final ApplicationEventPublisher eventPublisher;

    public List<FarmActivity> getActivities(String plantationId, String farmId, Boolean completed) {
        if (plantationId != null && completed != null) {
            return activityRepository.findByPlantationIdAndCompleted(plantationId, completed);
        } else if (plantationId != null) {
            return activityRepository.findByPlantationId(plantationId);
        } else if (farmId != null) {
            return activityRepository.findByFarmId(farmId);
        }
        return activityRepository.findAll();
    }

    public FarmActivity getActivityById(String id) {
        return activityRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Atividade não encontrada"));
    }

    public FarmActivity createActivity(CreateActivityRequest request) {
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

    public FarmActivity updateActivity(String id, UpdateActivityRequest request) {
        FarmActivity activity = getActivityById(id);

        if (request.title() != null) activity.setTitle(request.title());
        if (request.type() != null) activity.setType(request.type());
        if (request.date() != null) activity.setDate(request.date());
        if (request.priority() != null) activity.setPriority(request.priority());
        if (request.notes() != null) activity.setNotes(request.notes());
        if (request.costEstimate() != null) activity.setCostEstimate(request.costEstimate());
        if (request.completed() != null) {
            activity.setCompleted(request.completed());
            if (Boolean.TRUE.equals(request.completed())) {
                activity.setCompletedAt(Instant.now().toString());
            } else {
                activity.setCompletedAt(null);
            }
        }

        FarmActivity saved = activityRepository.save(activity);
        if (Boolean.TRUE.equals(saved.getCompleted())) {
            eventPublisher.publishEvent(new ActivityCompletedEvent(saved));
        }
        return saved;
    }

    public FarmActivity toggleStatus(String id, boolean completed) {
        FarmActivity activity = getActivityById(id);
        activity.setCompleted(completed);
        activity.setCompletedAt(completed ? Instant.now().toString() : null);
        FarmActivity saved = activityRepository.save(activity);
        if (completed) {
            eventPublisher.publishEvent(new ActivityCompletedEvent(saved));
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

    public void deleteActivity(String id) {
        FarmActivity activity = getActivityById(id);
        activityRepository.delete(activity);
    }
}


