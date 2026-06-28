package com.api.calendar;

import com.api.servicecatalog.Service;
import com.api.servicecatalog.ServiceDescriptionNormalizer;
import com.api.servicecatalog.ServiceRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class CalendarEventServiceMatcher {

    private final ServiceRepository serviceRepository;
    private final ServiceDescriptionNormalizer normalizer;

    public CalendarEventServiceMatcher(final ServiceRepository serviceRepository,
                                        final ServiceDescriptionNormalizer normalizer) {
        this.serviceRepository = serviceRepository;
        this.normalizer = normalizer;
    }

    public Optional<Service> matchService(final Long userId, final String eventTitle) {
        Optional<Service> matchedService = Optional.empty();
        if (eventTitle != null && !eventTitle.isBlank()) {
            final String normalizedTitle = normalizer.normalize(eventTitle);
            matchedService = serviceRepository.findByUserIdAndNormalizedDescription(userId, normalizedTitle);
        }
        return matchedService;
    }

    public Map<String, Service> servicesByNormalizedDescription(final Long userId) {
        final Map<String, Service> services = new LinkedHashMap<>();
        for (final Service service : serviceRepository.findAllByUserId(userId)) {
            services.putIfAbsent(service.getNormalizedDescription(), service);
        }
        return services;
    }
}
