package com.api.calendar;

import com.api.client.Client;
import com.api.client.ClientRepository;
import com.api.common.BusinessException;
import com.api.common.ResourceNotFoundException;
import com.api.servicecatalog.Service;
import com.api.servicecatalog.ServiceDescriptionNormalizer;
import com.api.servicecatalog.ServiceRepository;
import com.api.user.User;
import com.api.user.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class ManualAppointmentService {

    private static final ZoneId APPT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final String ERR_PAYLOAD = "Appointment payload is required";
    private static final String ERR_CLIENT_REQ = "Client is required";
    private static final String ERR_DATE_REQ = "Appointment date is required";
    private static final String ERR_TIME_RANGE = "End time must be greater than start time";
    private static final String ERR_USER = "User not found";
    private static final String ERR_CLIENT = "Client not found";
    private static final String ERR_SERVICE = "Service not found";
    private static final String ERR_SERVICE_REQ = "At least one service is required";
    private static final String DEFAULT_TITLE = "Agendamento";

    private final CalendarEventRepository eventRepository;
    private final ClientRepository clientRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final ServiceDescriptionNormalizer normalizer;

    public ManualAppointmentService(final CalendarEventRepository eventRepository,
                                    final ClientRepository clientRepository,
                                    final ServiceRepository serviceRepository,
                                    final UserRepository userRepository,
                                    final ServiceDescriptionNormalizer normalizer) {
        this.eventRepository = eventRepository;
        this.clientRepository = clientRepository;
        this.serviceRepository = serviceRepository;
        this.userRepository = userRepository;
        this.normalizer = normalizer;
    }

    @Transactional
    public CalendarEvent createManualAppointment(final Long userId, final ManualAppointmentRequest request) {
        validateRequest(request);
        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ERR_USER));
        final Client client = clientRepository.findByIdAndUserId(request.clientId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException(ERR_CLIENT));
        final List<Service> services = loadServices(userId, request.serviceIds());
        final String title = buildTitle(client, services);
        final String normalizedTitle = normalizer.normalize(title);
        final Instant eventStart = toInstant(request.appointmentDate(), request.startTime());
        final Instant eventEnd = toInstant(request.appointmentDate(), request.endTime());
        final CalendarEvent event = CalendarEvent.manual(user, title, normalizedTitle, eventStart, eventEnd);
        event.setClient(client);
        event.associateServices(services);
        return eventRepository.save(event);
    }

    private void validateRequest(final ManualAppointmentRequest request) {
        if (request == null) {
            throw new BusinessException(ERR_PAYLOAD);
        }
        requireValue(request.clientId(), ERR_CLIENT_REQ);
        requireValue(request.appointmentDate(), ERR_DATE_REQ);
        validateTimeRange(request.startTime(), request.endTime());
    }

    private void requireValue(final Object value, final String message) {
        if (value == null) {
            throw new BusinessException(message);
        }
    }

    private void validateTimeRange(final LocalTime startTime, final LocalTime endTime) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new BusinessException(ERR_TIME_RANGE);
        }
    }

    private List<Service> loadServices(final Long userId, final List<Long> rawServiceIds) {
        final List<Long> serviceIds = sanitizeServiceIds(rawServiceIds);
        final Map<Long, Service> servicesById = new LinkedHashMap<>();
        for (final Service service : serviceRepository.findByUserIdAndIdIn(userId, serviceIds)) {
            servicesById.putIfAbsent(service.getId(), service);
        }
        final List<Service> services = serviceIds.stream()
                .map(servicesById::get)
                .filter(Objects::nonNull)
                .toList();
        if (services.size() != serviceIds.size()) {
            throw new ResourceNotFoundException(ERR_SERVICE);
        }
        return services;
    }

    private List<Long> sanitizeServiceIds(final List<Long> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            throw new BusinessException(ERR_SERVICE_REQ);
        }
        final List<Long> uniqueIds = serviceIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));
        if (uniqueIds.isEmpty()) {
            throw new BusinessException(ERR_SERVICE_REQ);
        }
        return uniqueIds;
    }

    private String buildTitle(final Client client, final List<Service> services) {
        final String summary = services.stream()
                .map(Service::getDescription)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(description -> !description.isBlank())
                .reduce((left, right) -> left + " + " + right)
                .orElse(DEFAULT_TITLE);
        return client.getName().trim() + " - " + summary;
    }

    private Instant toInstant(final LocalDate date, final LocalTime time) {
        return date.atTime(time).atZone(APPT_ZONE).toInstant();
    }

    public record ManualAppointmentRequest(
            Long clientId,
            LocalDate appointmentDate,
            LocalTime startTime,
            LocalTime endTime,
            List<Long> serviceIds
    ) {
    }
}
