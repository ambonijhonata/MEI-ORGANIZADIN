package com.api.servicecatalog;

import com.api.calendar.CalendarEventRepository;
import com.api.calendar.CalendarEventReprocessor;
import com.api.calendar.CalendarEventServiceLinkRepository;
import com.api.calendar.SyncState;
import com.api.calendar.SyncStateRepository;
import com.api.common.BusinessException;
import com.api.common.ResourceNotFoundException;
import com.api.user.ApplicationUser;
import com.api.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ServiceCatalogService {
    private static final String EMPTY_DUP_MSG = "ServiÃ§o jÃ¡ cadastrado";
    private static final String DUPLICATE_SUFFIX = " jÃ¡ cadastrado";

    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final CalendarEventRepository eventRepository;
    private final CalendarEventServiceLinkRepository linkRepository;
    private final ServiceDescriptionNormalizer normalizer;
    private final CalendarEventReprocessor reprocessor;
    private final SyncStateRepository stateRepository;

    public ServiceCatalogService(final ServiceRepository serviceRepository,
                                  final UserRepository userRepository,
                                  final CalendarEventRepository eventRepository,
                                  final CalendarEventServiceLinkRepository linkRepository,
                                  final ServiceDescriptionNormalizer normalizer,
                                  final CalendarEventReprocessor reprocessor,
                                  final SyncStateRepository stateRepository) {
        this.serviceRepository = serviceRepository;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.linkRepository = linkRepository;
        this.normalizer = normalizer;
        this.reprocessor = reprocessor;
        this.stateRepository = stateRepository;
    }

    @Transactional
    public Service createService(final Long userId, final String description, final BigDecimal value) {
        final ApplicationUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("ApplicationUser not found"));

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
        Page<Service> result = serviceRepository.findByUserId(userId, pageable);
        if (description != null && !description.isBlank()) {
            result = serviceRepository.findByUserIdAndDescriptionContainingIgnoreCase(userId, description, pageable);
        }
        return result;
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

        final String priorNormalized = service.getNormalizedDescription();
        final String normalized = normalizer.normalize(description);

        serviceRepository.findByUserIdAndNormalizedText(userId, normalized)
                .filter(existing -> !existing.sameIdAs(serviceId))
                .ifPresent(existing -> {
                    throw new BusinessException(duplicateDescriptionMessage(description));
                });

        service.rename(description, normalized);
        service.reprice(value);

        final Service saved = serviceRepository.save(service);

        if (!normalized.equals(priorNormalized)) {
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
        BulkDeleteResult result = new BulkDeleteResult(0, 0);
        final Set<Long> uniqueIds = uniqueIds(serviceIds);
        if (!uniqueIds.isEmpty()) {
            result = deleteOwnedServices(userId, uniqueIds);
        }
        return result;
    }

    private boolean hasLinkedEvents(final Long serviceId) {
        return linkRepository.existsByServiceId(serviceId)
                || eventRepository.existsByServiceId(serviceId);
    }

    private String duplicateDescriptionMessage(final String description) {
        String message = EMPTY_DUP_MSG;
        final String label = description == null ? "" : description.trim();
        if (!label.isBlank()) {
            message = label + DUPLICATE_SUFFIX;
        }
        return message;
    }

    private void requestCatalogEnrichment(final Long userId, final ApplicationUser user) {
        final SyncState syncState = stateRepository.findByUserId(userId)
                .orElseGet(() -> new SyncState(user));
        syncState.catalogEnrichmentState().request();
        stateRepository.save(syncState);
    }

    private Set<Long> uniqueIds(final List<Long> serviceIds) {
        Set<Long> result = Set.of();
        if (serviceIds != null && !serviceIds.isEmpty()) {
            result = serviceIds.stream()
                    .filter(serviceId -> serviceId != null)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        return result;
    }

    private BulkDeleteResult deleteOwnedServices(final Long userId, final Set<Long> serviceIds) {
        final List<Service> ownedServices = serviceRepository.findByUserIdAndIdIn(userId, serviceIds);
        final List<Service> deletableServices = new ArrayList<>();
        int deleted = 0;
        int linkedCount = 0;

        for (final Service service : ownedServices) {
            final Long serviceId = service.getId();
            if (serviceId == null) {
                continue;
            }
            if (hasLinkedEvents(serviceId)) {
                linkedCount++;
                continue;
            }
            deletableServices.add(service);
        }

        if (!deletableServices.isEmpty()) {
            serviceRepository.deleteAll(deletableServices);
            deleted = deletableServices.size();
        }

        return new BulkDeleteResult(deleted, linkedCount);
    }

    public record BulkDeleteResult(int deleted, int linkedCount) {}
}
