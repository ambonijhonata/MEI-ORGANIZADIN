package com.api.calendar;

import com.api.servicecatalog.Service;
import com.api.servicecatalog.ServiceDescriptionNormalizer;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class CalendarEventReprocessor {

    private final CalendarEventRepository calendarEventRepository;
    private final CalendarEventServiceMatcher matcher;
    private final EventTitleParser titleParser;
    private final ServiceDescriptionNormalizer normalizer;

    public CalendarEventReprocessor(CalendarEventRepository calendarEventRepository,
                                    CalendarEventServiceMatcher matcher,
                                    EventTitleParser titleParser,
                                    ServiceDescriptionNormalizer normalizer) {
        this.calendarEventRepository = calendarEventRepository;
        this.matcher = matcher;
        this.titleParser = titleParser;
        this.normalizer = normalizer;
    }

    @Async
    @Transactional
    public void reprocessUnidentifiedEvents(Long userId) {
        List<CalendarEvent> unidentified = calendarEventRepository.findByUserIdAndIdentifiedFalse(userId);
        Map<String, Service> servicesByNormalizedDescription = matcher.servicesByNormalizedDescription(userId);

        for (CalendarEvent event : unidentified) {
            EventTitleParser.ParsedTitle parsed = titleParser.parse(event.getTitle());
            List<Service> matchedServices = resolveMatchedServices(parsed, servicesByNormalizedDescription);
            if (!matchedServices.isEmpty()) {
                event.associateServices(matchedServices);
            }
            event.setPaymentType(parsed.paymentType());
        }

        calendarEventRepository.saveAll(unidentified);
    }

    private List<Service> resolveMatchedServices(EventTitleParser.ParsedTitle parsed,
                                                 Map<String, Service> servicesByNormalizedDescription) {
        if (parsed.serviceNames().isEmpty()) {
            return List.of();
        }

        List<Service> matchedServices = new ArrayList<>(parsed.serviceNames().size());
        for (String serviceName : parsed.serviceNames()) {
            String normalizedServiceName = normalizer.normalize(serviceName);
            Service service = servicesByNormalizedDescription.get(normalizedServiceName);
            if (service != null) {
                matchedServices.add(service);
            }
        }
        return matchedServices;
    }
}
