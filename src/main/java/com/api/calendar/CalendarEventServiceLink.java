package com.api.calendar;

import com.api.servicecatalog.Service;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "calendar_event_services")
public class CalendarEventServiceLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long linkId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_event_id", nullable = false)
    private CalendarEvent calendarEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private Service service;

    @Column(name = "occurrence_index", nullable = false)
    private int occurrence;

    @Column(name = "service_description_snapshot", nullable = false, length = 500)
    private String serviceDesc;

    @Column(name = "service_value_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal serviceAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CalendarEventServiceLink() {}

    public CalendarEventServiceLink(final CalendarEvent event, final Service service) {
        this(event, service, 0);
    }

    public CalendarEventServiceLink(final CalendarEvent event, final Service service, final int occurrence) {
        this(event, service, occurrence, service.getDescription(), service.getValue());
    }

    public CalendarEventServiceLink(final CalendarEvent event,
                                    final Service service,
                                    final int occurrence,
                                    final String serviceDesc,
                                    final BigDecimal serviceAmount) {
        this.calendarEvent = event;
        this.service = service;
        this.occurrence = occurrence;
        assignSnapshot(serviceDesc, serviceAmount);
    }

    @PrePersist
    /* package */ void prePersist() {
        this.createdAt = Instant.now();
    }

    public static CalendarEventServiceLink materialize(final CalendarEvent event,
                                                       final Service service,
                                                       final int occurrence,
                                                       final String serviceDesc,
                                                       final BigDecimal serviceAmount) {
        return new CalendarEventServiceLink(event, service, occurrence, serviceDesc, serviceAmount);
    }

    public Long getId() { return linkId; }
    public CalendarEvent getCalendarEvent() { return calendarEvent; }
    public Service getService() { return service; }
    public int getOccurrenceIndex() { return occurrence; }
    public String getServiceDescriptionSnapshot() { return serviceDesc; }
    public BigDecimal getServiceValueSnapshot() { return serviceAmount; }

    public String descriptionOrBlank() {
        return serviceDesc != null ? serviceDesc : "";
    }

    public BigDecimal valueOrZero() {
        return serviceAmount != null ? serviceAmount : BigDecimal.ZERO;
    }

    public boolean hasService() {
        return service != null;
    }

    public String serviceIdentity() {
        return CalendarEventServiceOccurrences.identityFor(service);
    }

    public boolean refersTo(final Service candidate) {
        final String identity = serviceIdentity();
        return identity != null && identity.equals(CalendarEventServiceOccurrences.identityFor(candidate));
    }

    public void refreshSnapshot(final String desc, final BigDecimal amount) {
        assignSnapshot(desc, amount);
    }

    private void assignSnapshot(final String desc, final BigDecimal amount) {
        this.serviceDesc = desc;
        this.serviceAmount = amount;
    }
}
