package com.api.calendar;

import com.api.client.Client;
import com.api.servicecatalog.Service;
import com.api.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "calendar_events", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "google_event_id"})
})
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "google_event_id", nullable = false, length = 1024)
    private String googleEventId;

    @Column(length = 1000)
    private String title;

    @Column(name = "normalized_title", length = 1000)
    private String normalizedTitle;

    @Column(name = "event_start", nullable = false)
    private Instant eventStart;

    @Column(name = "event_end")
    private Instant eventEnd;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    // Legacy single-service fields (kept for backward compatibility with existing data)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private Service service;

    @Column(name = "service_description_snapshot", length = 500)
    private String serviceDescriptionSnapshot;

    @Column(name = "service_value_snapshot", precision = 12, scale = 2)
    private BigDecimal serviceValueSnapshot;

    @OneToMany(mappedBy = "calendarEvent", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CalendarEventServiceLink> serviceLinks = new ArrayList<>();

    @Column(nullable = false)
    private boolean identified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CalendarEvent() {}

    public CalendarEvent(User user, String googleEventId, String title, String normalizedTitle,
                          Instant eventStart, Instant eventEnd) {
        this.user = user;
        this.googleEventId = googleEventId;
        this.title = title;
        this.normalizedTitle = normalizedTitle;
        this.eventStart = eventStart;
        this.eventEnd = eventEnd;
    }

    @PrePersist
    void prePersist() {
        var now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public void associateService(Service service) {
        this.service = service;
        this.serviceDescriptionSnapshot = service.getDescription();
        this.serviceValueSnapshot = service.getValue();
        this.identified = true;
    }

    public void associateServices(List<Service> services) {
        this.serviceLinks.clear();
        BigDecimal totalValue = BigDecimal.ZERO;
        for (Service s : services) {
            this.serviceLinks.add(new CalendarEventServiceLink(this, s));
            totalValue = totalValue.add(s.getValue());
        }
        if (!services.isEmpty()) {
            this.service = services.get(0);
            this.serviceDescriptionSnapshot = services.get(0).getDescription();
            this.serviceValueSnapshot = totalValue;
            this.identified = true;
        }
    }

    public void clearServiceAssociation() {
        this.service = null;
        this.serviceDescriptionSnapshot = null;
        this.serviceValueSnapshot = null;
        this.serviceLinks.clear();
        this.identified = false;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public void updateFromGoogle(String title, String normalizedTitle, Instant eventStart, Instant eventEnd) {
        this.title = title;
        this.normalizedTitle = normalizedTitle;
        this.eventStart = eventStart;
        this.eventEnd = eventEnd;
    }

    public void markIdentified(boolean identified) {
        this.identified = identified;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getGoogleEventId() { return googleEventId; }
    public String getTitle() { return title; }
    public String getNormalizedTitle() { return normalizedTitle; }
    public Instant getEventStart() { return eventStart; }
    public Instant getEventEnd() { return eventEnd; }
    public Client getClient() { return client; }
    public Service getService() { return service; }
    public String getServiceDescriptionSnapshot() { return serviceDescriptionSnapshot; }
    public BigDecimal getServiceValueSnapshot() { return serviceValueSnapshot; }
    public List<CalendarEventServiceLink> getServiceLinks() { return serviceLinks; }
    public boolean isIdentified() { return identified; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
