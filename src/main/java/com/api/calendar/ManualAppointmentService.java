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

@SuppressWarnings({"PMD.CyclomaticComplexity", "PMD.LongVariable"})
@Component
public class ManualAppointmentService {

    private static final ZoneId APPOINTMENT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final CalendarEventRepository calendarEventRepository;
    private final ClientRepository clientRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final ServiceDescriptionNormalizer normalizer;

    public ManualAppointmentService(final CalendarEventRepository calendarEventRepository,
                                    final ClientRepository clientRepository,
                                    final ServiceRepository serviceRepository,
                                    final UserRepository userRepository,
                                    final ServiceDescriptionNormalizer normalizer) {
        this.calendarEventRepository = calendarEventRepository;
        this.clientRepository = clientRepository;
        this.serviceRepository = serviceRepository;
        this.userRepository = userRepository;
        this.normalizer = normalizer;
    }

    @Transactional
    public CalendarEvent createManualAppointment(final Long userId, final ManualAppointmentRequest request) {
        if (request == null) {
            throw new BusinessException("Appointment payload is required");
        }
        if (request.clientId() == null) {
            throw new BusinessException("Client is required");
        }
        if (request.appointmentDate() == null) {
            throw new BusinessException("Appointment date is required");
        }

        final LocalTime startTime = request.startTime();
        final LocalTime endTime = request.endTime();
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new BusinessException("End time must be greater than start time");
        }

        final User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        final Client client = clientRepository.findByIdAndUserId(request.clientId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));

        final List<Long> orderedServiceIds = sanitizeServiceIds(request.serviceIds());
        final Map<Long, Service> ownedServicesById = new LinkedHashMap<>();
        for (final Service service : serviceRepository.findByUserIdAndIdIn(userId, orderedServiceIds)) {
            ownedServicesById.putIfAbsent(service.getId(), service);
        }

        final List<Service> orderedServices = orderedServiceIds.stream()
                .map(ownedServicesById::get)
                .filter(Objects::nonNull)
                .toList();

        if (orderedServices.size() != orderedServiceIds.size()) {
            throw new ResourceNotFoundException("Service not found");
        }

        final String title = buildTitle(client, orderedServices);
        final String normalizedTitle = normalizer.normalize(title);
        final Instant eventStart = toAppointmentInstant(request.appointmentDate(), startTime);
        final Instant eventEnd = toAppointmentInstant(request.appointmentDate(), endTime);

        final CalendarEvent event = CalendarEvent.manual(user, title, normalizedTitle, eventStart, eventEnd);
        event.setClient(client);
        event.associateServices(orderedServices);

        return calendarEventRepository.save(event);
    }

    private List<Long> sanitizeServiceIds(final List<Long> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            throw new BusinessException("At least one service is required");
        }
        final List<Long> orderedUniqueIds = serviceIds.stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));
        if (orderedUniqueIds.isEmpty()) {
            throw new BusinessException("At least one service is required");
        }
        return orderedUniqueIds;
    }

    private String buildTitle(final Client client, final List<Service> services) {
        final String serviceSummary = services.stream()
                .map(Service::getDescription)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(description -> !description.isBlank())
                .reduce((left, right) -> left + " + " + right)
                .orElse("Agendamento");
        return client.getName().trim() + " - " + serviceSummary;
    }

    private Instant toAppointmentInstant(final LocalDate appointmentDate, final LocalTime appointmentTime) {
        return appointmentDate.atTime(appointmentTime).atZone(APPOINTMENT_ZONE).toInstant();
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
