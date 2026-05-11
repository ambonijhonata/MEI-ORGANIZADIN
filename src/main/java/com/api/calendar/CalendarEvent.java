package com.api.calendar;

import com.api.client.Client;
import com.api.servicecatalog.Service;
import com.api.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", length = 20)
    private PaymentType paymentType;

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

    // Service-link replacement for persisted events is repository-driven during sync/reprocessing.
    // Avoid orphanRemoval here so Hibernate does not schedule a second delete for rows already
    // removed through the explicit bulk-delete path.
    @OneToMany(mappedBy = "calendarEvent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CalendarEventServiceLink> serviceLinks = new ArrayList<>();

    @OneToMany(mappedBy = "calendarEvent", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CalendarEventPayment> payments = new ArrayList<>();

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
        if (services == null || services.isEmpty()) {
            clearServiceAssociation();
            return;
        }

        this.serviceLinks.clear();
        BigDecimal totalValue = BigDecimal.ZERO;
        Map<String, Integer> occurrencesByIdentity = new HashMap<>();
        for (Service s : services) {
            int occurrenceIndex = nextOccurrenceIndex(occurrencesByIdentity, serviceIdentity(s));
            this.serviceLinks.add(new CalendarEventServiceLink(this, s, occurrenceIndex));
            totalValue = totalValue.add(s.getValue());
        }
        this.service = services.get(0);
        this.serviceDescriptionSnapshot = services.get(0).getDescription();
        this.serviceValueSnapshot = totalValue;
        this.identified = true;
    }

    public boolean enrichServices(List<Service> services) {
        if (services == null || services.isEmpty()) {
            return false;
        }

        if (!this.identified || (this.service == null && this.serviceLinks.isEmpty())) {
            associateServices(services);
            return true;
        }

        ensureLegacyAssociationBackfilledIntoLinks();

        Map<String, Integer> existingServiceIdentities = new HashMap<>();
        for (CalendarEventServiceLink serviceLink : this.serviceLinks) {
            incrementOccurrence(existingServiceIdentities, serviceIdentity(serviceLink.getService()));
        }

        boolean changed = false;
        for (Service service : services) {
            String identity = serviceIdentity(service);
            if (identity == null) {
                continue;
            }
            int existingCount = existingServiceIdentities.getOrDefault(identity, 0);
            int requestedCount = countOccurrences(services, identity);
            while (existingCount < requestedCount) {
                this.serviceLinks.add(new CalendarEventServiceLink(this, service, existingCount));
                changed = true;
                existingCount++;
            }
            existingServiceIdentities.put(identity, existingCount);
        }

        if (!changed) {
            return false;
        }

        if (this.service == null) {
            this.service = services.get(0);
        }
        if (this.serviceDescriptionSnapshot == null || this.serviceDescriptionSnapshot.isBlank()) {
            this.serviceDescriptionSnapshot = this.service != null ? this.service.getDescription() : services.get(0).getDescription();
        }

        this.serviceValueSnapshot = totalLinkedSnapshotValue();
        this.identified = true;
        return true;
    }

    public void clearServiceAssociation() {
        this.service = null;
        this.serviceDescriptionSnapshot = null;
        this.serviceValueSnapshot = null;
        this.serviceLinks.clear();
        this.identified = false;
    }

    public void replacePayments(List<CalendarEventPayment> newPayments) {
        this.payments.clear();
        this.payments.addAll(newPayments);
    }

    public void clearPayments() {
        this.payments.clear();
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public void setPaymentType(PaymentType paymentType) {
        this.paymentType = paymentType;
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

    private void ensureLegacyAssociationBackfilledIntoLinks() {
        if (!this.serviceLinks.isEmpty() || this.service == null) {
            return;
        }

        String descriptionSnapshot = this.serviceDescriptionSnapshot != null
                ? this.serviceDescriptionSnapshot
                : this.service.getDescription();
        BigDecimal valueSnapshot = this.serviceValueSnapshot != null
                ? this.serviceValueSnapshot
                : this.service.getValue();
        this.serviceLinks.add(new CalendarEventServiceLink(this, this.service, 0, descriptionSnapshot, valueSnapshot));
    }

    private BigDecimal totalLinkedSnapshotValue() {
        BigDecimal total = BigDecimal.ZERO;
        for (CalendarEventServiceLink serviceLink : this.serviceLinks) {
            if (serviceLink.getServiceValueSnapshot() != null) {
                total = total.add(serviceLink.getServiceValueSnapshot());
            }
        }
        return total;
    }

    private String serviceIdentity(Service service) {
        if (service == null) {
            return null;
        }
        if (service.getId() != null) {
            return "id:" + service.getId();
        }
        if (service.getNormalizedDescription() != null && !service.getNormalizedDescription().isBlank()) {
            return "normalized:" + service.getNormalizedDescription();
        }
        if (service.getDescription() != null && !service.getDescription().isBlank()) {
            return "description:" + service.getDescription();
        }
        if (service.getValue() != null) {
            return "value:" + service.getValue().stripTrailingZeros().toPlainString();
        }
        return null;
    }

    private int nextOccurrenceIndex(Map<String, Integer> occurrencesByIdentity, String identity) {
        if (identity == null) {
            return 0;
        }
        int next = occurrencesByIdentity.getOrDefault(identity, 0);
        occurrencesByIdentity.put(identity, next + 1);
        return next;
    }

    private void incrementOccurrence(Map<String, Integer> occurrencesByIdentity, String identity) {
        if (identity == null) {
            return;
        }
        occurrencesByIdentity.put(identity, occurrencesByIdentity.getOrDefault(identity, 0) + 1);
    }

    private int countOccurrences(List<Service> services, String identity) {
        int count = 0;
        for (Service service : services) {
            if (identity != null && identity.equals(serviceIdentity(service))) {
                count++;
            }
        }
        return count;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getGoogleEventId() { return googleEventId; }
    public String getTitle() { return title; }
    public String getNormalizedTitle() { return normalizedTitle; }
    public Instant getEventStart() { return eventStart; }
    public Instant getEventEnd() { return eventEnd; }
    public PaymentType getPaymentType() { return paymentType; }
    public Client getClient() { return client; }
    public Service getService() { return service; }
    public String getServiceDescriptionSnapshot() { return serviceDescriptionSnapshot; }
    public BigDecimal getServiceValueSnapshot() { return serviceValueSnapshot; }
    public List<CalendarEventServiceLink> getServiceLinks() { return serviceLinks; }
    public List<CalendarEventPayment> getPayments() { return payments; }
    public boolean isIdentified() { return identified; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
