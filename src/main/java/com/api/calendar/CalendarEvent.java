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
@SuppressWarnings({"PMD.CognitiveComplexity", "PMD.CommentDefaultAccessModifier", "PMD.CyclomaticComplexity", "PMD.GodClass", "PMD.ImmutableField", "PMD.LawOfDemeter", "PMD.LongVariable", "PMD.NullAssignment", "PMD.OnlyOneReturn", "PMD.RedundantFieldInitializer", "PMD.ShortVariable", "PMD.TooManyFields", "PMD.TooManyMethods", "PMD.UseExplicitTypes"})
public class CalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private CalendarEventSource source = CalendarEventSource.GOOGLE;

    @Column(name = "google_event_id", length = 1024)
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

    public CalendarEvent(final User user, final String googleEventId, final String title, final String normalizedTitle,
                         final Instant eventStart, final Instant eventEnd) {
        this(user, CalendarEventSource.GOOGLE, googleEventId, title, normalizedTitle, eventStart, eventEnd);
    }

    public CalendarEvent(final User user,
                         final CalendarEventSource source,
                         final String googleEventId,
                         final String title,
                         final String normalizedTitle,
                         final Instant eventStart,
                         final Instant eventEnd) {
        this.user = user;
        this.source = source;
        this.googleEventId = googleEventId;
        this.title = title;
        this.normalizedTitle = normalizedTitle;
        this.eventStart = eventStart;
        this.eventEnd = eventEnd;
    }

    public static CalendarEvent manual(final User user,
                                       final String title,
                                       final String normalizedTitle,
                                       final Instant eventStart,
                                       final Instant eventEnd) {
        return new CalendarEvent(user, CalendarEventSource.MANUAL, null, title, normalizedTitle, eventStart, eventEnd);
    }

    @PrePersist
    void prePersist() {
        final var now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public void associateService(final Service service) {
        this.service = service;
        this.serviceDescriptionSnapshot = service.getDescription();
        this.serviceValueSnapshot = service.getValue();
        this.identified = true;
    }

    public void associateServices(final List<Service> services) {
        if (services == null || services.isEmpty()) {
            clearServiceAssociation();
            return;
        }

        this.serviceLinks.clear();
        BigDecimal totalValue = BigDecimal.ZERO;
        final Map<String, Integer> occurrencesByIdentity = new HashMap<>();
        for (final Service s : services) {
            final int occurrenceIndex = nextOccurrenceIndex(occurrencesByIdentity, serviceIdentity(s));
            this.serviceLinks.add(new CalendarEventServiceLink(this, s, occurrenceIndex));
            totalValue = totalValue.add(s.getValue());
        }
        this.service = services.get(0);
        this.serviceDescriptionSnapshot = services.get(0).getDescription();
        this.serviceValueSnapshot = totalValue;
        this.identified = true;
    }

    public boolean enrichServices(final List<Service> services) {
        if (services == null || services.isEmpty()) {
            return false;
        }

        if (!this.identified || (this.service == null && this.serviceLinks.isEmpty())) {
            associateServices(services);
            return true;
        }

        ensureLegacyAssociationBackfilledIntoLinks();

        final Map<String, Integer> existingServiceIdentities = new HashMap<>();
        for (final CalendarEventServiceLink serviceLink : this.serviceLinks) {
            incrementOccurrence(existingServiceIdentities, serviceIdentity(serviceLink.getService()));
        }

        boolean changed = false;
        for (final Service service : services) {
            final String identity = serviceIdentity(service);
            if (identity == null) {
                continue;
            }
            int existingCount = existingServiceIdentities.getOrDefault(identity, 0);
            final int requestedCount = countOccurrences(services, identity);
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

    public void replacePayments(final List<CalendarEventPayment> newPayments) {
        this.payments.clear();
        this.payments.addAll(newPayments);
    }

    public void clearPayments() {
        this.payments.clear();
    }

    public void setClient(final Client client) {
        this.client = client;
    }

    public void setPaymentType(final PaymentType paymentType) {
        this.paymentType = paymentType;
    }

    public void updateFromGoogle(final String title, final String normalizedTitle, final Instant eventStart, final Instant eventEnd) {
        this.title = title;
        this.normalizedTitle = normalizedTitle;
        this.eventStart = eventStart;
        this.eventEnd = eventEnd;
    }

    public void markIdentified(final boolean identified) {
        this.identified = identified;
    }

    public boolean isGoogleOrigin() {
        return source == CalendarEventSource.GOOGLE && googleEventId != null && !googleEventId.isBlank();
    }

    public CalendarEventReadModel toReadModel(final BigDecimal paidAmount) {
        final BigDecimal totalAmount = serviceValueSnapshot != null ? serviceValueSnapshot : BigDecimal.ZERO;
        final String paymentTypeName = paymentType != null ? paymentType.name() : null;
        return new CalendarEventReadModel(
                id,
                googleEventId,
                title,
                formatInstant(eventStart),
                formatInstant(eventEnd),
                identified,
                serviceDescriptionSnapshot,
                totalAmount,
                paymentTypeName,
                paidAmount
        );
    }

    private void ensureLegacyAssociationBackfilledIntoLinks() {
        if (!this.serviceLinks.isEmpty() || this.service == null) {
            return;
        }

        final String descriptionSnapshot = this.serviceDescriptionSnapshot != null
                ? this.serviceDescriptionSnapshot
                : this.service.getDescription();
        final BigDecimal valueSnapshot = this.serviceValueSnapshot != null
                ? this.serviceValueSnapshot
                : this.service.getValue();
        this.serviceLinks.add(new CalendarEventServiceLink(this, this.service, 0, descriptionSnapshot, valueSnapshot));
    }

    private BigDecimal totalLinkedSnapshotValue() {
        BigDecimal total = BigDecimal.ZERO;
        for (final CalendarEventServiceLink serviceLink : this.serviceLinks) {
            if (serviceLink.getServiceValueSnapshot() != null) {
                total = total.add(serviceLink.getServiceValueSnapshot());
            }
        }
        return total;
    }

    private String serviceIdentity(final Service service) {
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

    private int nextOccurrenceIndex(final Map<String, Integer> occurrencesByIdentity, final String identity) {
        if (identity == null) {
            return 0;
        }
        final int next = occurrencesByIdentity.getOrDefault(identity, 0);
        occurrencesByIdentity.put(identity, next + 1);
        return next;
    }

    private void incrementOccurrence(final Map<String, Integer> occurrencesByIdentity, final String identity) {
        if (identity == null) {
            return;
        }
        occurrencesByIdentity.put(identity, occurrencesByIdentity.getOrDefault(identity, 0) + 1);
    }

    private int countOccurrences(final List<Service> services, final String identity) {
        int count = 0;
        for (final Service service : services) {
            if (identity != null && identity.equals(serviceIdentity(service))) {
                count++;
            }
        }
        return count;
    }

    private String formatInstant(final Instant value) {
        return value != null ? value.toString() : null;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public CalendarEventSource getSource() { return source; }
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
