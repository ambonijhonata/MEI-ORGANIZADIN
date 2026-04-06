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

    public CalendarEventServiceMatcher(ServiceRepository serviceRepository,
                                        ServiceDescriptionNormalizer normalizer) {
        this.serviceRepository = serviceRepository;
        this.normalizer = normalizer;
    }

    public Optional<Service> matchService(Long userId, String eventTitle) {
        if (eventTitle == null || eventTitle.isBlank()) {
            return Optional.empty();
        }
        String normalizedTitle = normalizer.normalize(eventTitle);
        return serviceRepository.findByUserIdAndNormalizedDescription(userId, normalizedTitle);
    }

    public Map<String, Service> servicesByNormalizedDescription(Long userId) {
        Map<String, Service> services = new LinkedHashMap<>();
        for (Service service : serviceRepository.findAllByUserId(userId)) {
            services.putIfAbsent(service.getNormalizedDescription(), service);
        }
        return services;
    }
}
