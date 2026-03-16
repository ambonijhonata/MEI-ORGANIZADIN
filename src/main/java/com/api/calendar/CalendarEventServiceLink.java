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
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_event_id", nullable = false)
    private CalendarEvent calendarEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private Service service;

    @Column(name = "service_description_snapshot", nullable = false, length = 500)
    private String serviceDescriptionSnapshot;

    @Column(name = "service_value_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal serviceValueSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CalendarEventServiceLink() {}

    public CalendarEventServiceLink(CalendarEvent calendarEvent, Service service) {
        this.calendarEvent = calendarEvent;
        this.service = service;
        this.serviceDescriptionSnapshot = service.getDescription();
        this.serviceValueSnapshot = service.getValue();
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public CalendarEvent getCalendarEvent() { return calendarEvent; }
    public Service getService() { return service; }
    public String getServiceDescriptionSnapshot() { return serviceDescriptionSnapshot; }
    public BigDecimal getServiceValueSnapshot() { return serviceValueSnapshot; }
}
