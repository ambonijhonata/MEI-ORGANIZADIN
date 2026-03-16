package com.api.servicecatalog;

import com.api.calendar.CalendarEventRepository;
import com.api.calendar.CalendarEventReprocessor;
import com.api.calendar.CalendarEventServiceLinkRepository;
import com.api.common.BusinessException;
import com.api.common.ResourceNotFoundException;
import com.api.user.User;
import com.api.user.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

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
            throw new BusinessException("Service with this description already exists");
        }

        Service service = new Service(user, description, normalized, value);
        Service saved = serviceRepository.save(service);

        reprocessor.reprocessUnidentifiedEvents(userId);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Service> listServices(Long userId, String description, Sort sort) {
        if (description != null && !description.isBlank()) {
            return serviceRepository.findByUserIdAndDescriptionContainingIgnoreCase(userId, description, sort);
        }
        return serviceRepository.findByUserId(userId, sort);
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
                    throw new BusinessException("Service with this description already exists");
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

        boolean hasLinkedEvents = serviceLinkRepository.existsByServiceId(serviceId)
                || calendarEventRepository.existsByServiceId(serviceId);
        if (hasLinkedEvents) {
            throw new BusinessException("Cannot delete service with linked calendar events");
        }

        serviceRepository.delete(service);
    }
}
