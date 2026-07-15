package com.api.servicecatalog;

import com.api.calendar.CalendarEventRepository;
import com.api.calendar.CalendarEventReprocessor;
import com.api.calendar.CalendarEventServiceLinkRepository;
import com.api.calendar.SyncState;
import com.api.calendar.SyncStateRepository;
import com.api.common.BusinessException;
import com.api.common.ResourceNotFoundException;
import com.api.user.User;
import com.api.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings({"PMD.LinguisticNaming", "PMD.LongVariable", "PMD.OnlyOneReturn"})
@Component
public class ServiceCatalogService {

    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final CalendarEventServiceLinkRepository serviceLinkRepository;
    private final ServiceDescriptionNormalizer normalizer;
    private final CalendarEventReprocessor reprocessor;
    private final SyncStateRepository syncStateRepository;

    public ServiceCatalogService(final ServiceRepository serviceRepository,
                                  final UserRepository userRepository,
                                  final CalendarEventRepository calendarEventRepository,
                                  final CalendarEventServiceLinkRepository serviceLinkRepository,
                                  final ServiceDescriptionNormalizer normalizer,
                                  final CalendarEventReprocessor reprocessor,
                                  final SyncStateRepository syncStateRepository) {
        this.serviceRepository = serviceRepository;
        this.userRepository = userRepository;
        this.calendarEventRepository = calendarEventRepository;
        this.serviceLinkRepository = serviceLinkRepository;
        this.normalizer = normalizer;
        this.reprocessor = reprocessor;
        this.syncStateRepository = syncStateRepository;
    }

    @Transactional
    public Service createService(final Long userId, final String description, final BigDecimal value) {
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        final String normalized = normalizer.normalize(description);

        if (serviceRepository.existsByUserIdAndNormalizedText(userId, normalized)) {
            throw new BusinessException(duplicateDescriptionMessage(description));
        }

        final Service service = new Service(user, description, normalized, value);
        final Service saved = serviceRepository.save(service);

        requestCatalogEnrichment(userId, user);
        reprocessor.enrichSynchronizedAppointments(userId);

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Service> listServices(final Long userId, final String description, final Pageable pageable) {
        if (description != null && !description.isBlank()) {
            return serviceRepository.findByUserIdAndDescriptionContainingIgnoreCase(userId, description, pageable);
        }
        return serviceRepository.findByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Service getService(final Long userId, final Long serviceId) {
        return serviceRepository.findByIdAndUserId(serviceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
    }

    @Transactional
    public Service updateService(final Long userId, final Long serviceId, final String description, final BigDecimal value) {
        final Service service = serviceRepository.findByIdAndUserId(serviceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        final String previousNormalizedDescription = service.getNormalizedDescription();
        final String normalized = normalizer.normalize(description);

        serviceRepository.findByUserIdAndNormalizedText(userId, normalized)
                .filter(existing -> !existing.sameIdAs(serviceId))
                .ifPresent(existing -> {
                    throw new BusinessException(duplicateDescriptionMessage(description));
                });

        service.rename(description, normalized);
        service.reprice(value);

        final Service saved = serviceRepository.save(service);

        if (!normalized.equals(previousNormalizedDescription)) {
            requestCatalogEnrichment(userId, service.getUser());
            reprocessor.enrichSynchronizedAppointments(userId);
        }

        return saved;
    }

    @Transactional
    public void deleteService(final Long userId, final Long serviceId) {
        final Service service = serviceRepository.findByIdAndUserId(serviceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));

        if (hasLinkedEvents(serviceId)) {
            throw new BusinessException("Cannot delete service with linked calendar events");
        }

        serviceRepository.delete(service);
    }

    @Transactional
    public BulkDeleteResult deleteServices(final Long userId, final List<Long> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return new BulkDeleteResult(0, 0);
        }

        final Set<Long> uniqueIds = serviceIds.stream()
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (uniqueIds.isEmpty()) {
            return new BulkDeleteResult(0, 0);
        }

        final List<Service> ownedServices = serviceRepository.findByUserIdAndIdIn(userId, uniqueIds);
        int deleted = 0;
        int hasLink = 0;

        for (final Service service : ownedServices) {
            final Long serviceId = service.getId();
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

    private boolean hasLinkedEvents(final Long serviceId) {
        return serviceLinkRepository.existsByServiceId(serviceId)
                || calendarEventRepository.existsByServiceId(serviceId);
    }

    private String duplicateDescriptionMessage(final String description) {
        final String trimmedDescription = description == null ? "" : description.trim();
        return trimmedDescription.isBlank()
                ? "Serviço já cadastrado"
                : trimmedDescription + " já cadastrado";
    }

    private void requestCatalogEnrichment(final Long userId, final User user) {
        final SyncState syncState = syncStateRepository.findByUserId(userId)
                .orElseGet(() -> new SyncState(user));
        syncState.catalogEnrichmentState().request();
        syncStateRepository.save(syncState);
    }

    public record BulkDeleteResult(int deleted, int hasLink) {}
}
