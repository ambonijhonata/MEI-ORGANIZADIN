package com.api.servicecatalog;

import com.api.calendar.CalendarEventRepository;
import com.api.calendar.CalendarEventReprocessor;
import com.api.calendar.CalendarEventServiceLinkRepository;
import com.api.common.BusinessException;
import com.api.common.ResourceNotFoundException;
import com.api.user.User;
import com.api.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ServiceCatalogService {

    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final CalendarEventServiceLinkRepository serviceLinkRepository;
    private final ServiceDescriptionNormalizer normalizer;
    private final CalendarEventReprocessor reprocessor;

    public ServiceCatalogService(ServiceRepository serviceRepository,
                                  UserRepository userRepository,
                                  CalendarEventRepository calendarEventRepository,
                                  CalendarEventServiceLinkRepository serviceLinkRepository,
                                  ServiceDescriptionNormalizer normalizer,
                                  CalendarEventReprocessor reprocessor) {
        this.serviceRepository = serviceRepository;
        this.userRepository = userRepository;
        this.calendarEventRepository = calendarEventRepository;
        this.serviceLinkRepository = serviceLinkRepository;
        this.normalizer = normalizer;
        this.reprocessor = reprocessor;
    }

    @Transactional
    public Service createService(Long userId, String description, BigDecimal value) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String normalized = normalizer.normalize(description);

        if (serviceRepository.existsByUserIdAndNormalizedDescription(userId, normalized)) {
            throw new BusinessException(duplicateDescriptionMessage(description));
        }

        Service service = new Service(user, description, normalized, value);
        Service saved = serviceRepository.save(service);

        reprocessor.reprocessUnidentifiedEvents(userId);

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Service> listServices(Long userId, String description, Pageable pageable) {
        if (description != null && !description.isBlank()) {
            return serviceRepository.findByUserIdAndDescriptionContainingIgnoreCase(userId, description, pageable);
        }
        return serviceRepository.findByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Service getService(Long userId, Long serviceId) {
        return serviceRepository.findByIdAndUserId(serviceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
    }

    @Transactional
    public Service updateService(Long userId, Long serviceId, String description, BigDecimal value) {
        Service service = serviceRepository.findByIdAndUserId(serviceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        String normalized = normalizer.normalize(description);

        serviceRepository.findByUserIdAndNormalizedDescription(userId, normalized)
                .filter(existing -> !existing.getId().equals(serviceId))
                .ifPresent(existing -> {
                    throw new BusinessException(duplicateDescriptionMessage(description));
                });

        service.setDescription(description);
        service.setNormalizedDescription(normalized);
        service.setValue(value);

        Service saved = serviceRepository.save(service);

        reprocessor.reprocessUnidentifiedEvents(userId);

        return saved;
    }

    @Transactional
    public void deleteService(Long userId, Long serviceId) {
        Service service = serviceRepository.findByIdAndUserId(serviceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        if (hasLinkedEvents(serviceId)) {
            throw new BusinessException("Cannot delete service with linked calendar events");
        }

        serviceRepository.delete(service);
    }

    @Transactional
    public BulkDeleteResult deleteServices(Long userId, List<Long> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return new BulkDeleteResult(0, 0);
        }

        Set<Long> uniqueIds = serviceIds.stream()
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (uniqueIds.isEmpty()) {
            return new BulkDeleteResult(0, 0);
        }

        List<Service> ownedServices = serviceRepository.findByUserIdAndIdIn(userId, uniqueIds);
        int deleted = 0;
        int hasLink = 0;

        for (Service service : ownedServices) {
            Long serviceId = service.getId();
            if (serviceId == null) {
                continue;
            }
            if (hasLinkedEvents(serviceId)) {
                hasLink++;
                continue;
            }
            serviceRepository.delete(service);
            deleted++;
        }

        return new BulkDeleteResult(deleted, hasLink);
    }

    private boolean hasLinkedEvents(Long serviceId) {
        return serviceLinkRepository.existsByServiceId(serviceId)
                || calendarEventRepository.existsByServiceId(serviceId);
    }

    private String duplicateDescriptionMessage(String description) {
        String trimmedDescription = description == null ? "" : description.trim();
        return trimmedDescription.isBlank()
                ? "Serviço já cadastrado"
                : trimmedDescription + " já cadastrado";
    }

    public record BulkDeleteResult(int deleted, int hasLink) {}
}
