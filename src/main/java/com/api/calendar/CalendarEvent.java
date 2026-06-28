package com.api.calendar;

import com.api.client.Client;
import com.api.servicecatalog.Service;
import com.api.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "calendar_events", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "google_event_id"})
})
@SuppressWarnings({"PMD.ShortVariable", "PMD.TooManyMethods"})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long eventId;

    @Transient
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private CalendarEventSource source;

    @Column(name = "google_event_id", length = 1024)
    private String googleEventId;

    @Embedded
    private CalendarEventLabel label = CalendarEventLabel.empty();

    @Embedded
    private CalendarEventTiming timing = CalendarEventTiming.empty();

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", length = 20)
    @Setter(AccessLevel.PACKAGE)
    private PaymentType paymentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    @Setter(AccessLevel.PACKAGE)
    private Client client;

    // Legacy single-service fields (kept for backward compatibility with existing data)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private Service service;

    @Embedded
    private CalendarEventServiceSnapshot snapshot = CalendarEventServiceSnapshot.empty();

    // Service-link replacement for persisted events is repository-driven during sync/reprocessing.
    // Avoid orphanRemoval here so Hibernate does not schedule a second delete for rows already
    // removed through the explicit bulk-delete path.
    @OneToMany(mappedBy = "calendarEvent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private final List<CalendarEventServiceLink> serviceLinks = new ArrayList<>();

    @OneToMany(mappedBy = "calendarEvent", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private final List<CalendarEventPayment> payments = new ArrayList<>();

    @Column(nullable = false)
    @Setter(AccessLevel.PACKAGE)
    private boolean identified;

    @Embedded
    private final CalendarEventAudit audit = CalendarEventAudit.empty();

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
        this.label = CalendarEventLabel.create(title, normalizedTitle);
        this.timing = CalendarEventTiming.create(eventStart, eventEnd);
    }

    public static CalendarEvent manual(final User user,
                                       final String title,
                                       final String normalizedTitle,
                                       final Instant eventStart,
                                       final Instant eventEnd) {
        return new CalendarEvent(user, CalendarEventSource.MANUAL, null, title, normalizedTitle, eventStart, eventEnd);
    }

    public Long getId() {
        return eventId != null ? eventId : id;
    }

    public String getTitle() {
        return label.getTitle();
    }

    public String getNormalizedTitle() {
        return label.getNormalizedTitle();
    }

    public Instant getEventStart() {
        return timing.getStart();
    }

    public Instant getEventEnd() {
        return timing.getEnd();
    }

    public boolean matchesCoreData(final String title,
                                   final String normalizedTitle,
                                   final Instant eventStart,
                                   final Instant eventEnd) {
        return java.util.Objects.equals(label.getTitle(), title)
                && java.util.Objects.equals(label.getNormalizedTitle(), normalizedTitle)
                && java.util.Objects.equals(timing.getStart(), eventStart)
                && java.util.Objects.equals(timing.getEnd(), eventEnd);
    }

    @PrePersist
    protected void prePersist() {
        this.audit.touchOnCreate();
    }

    @PreUpdate
    protected void preUpdate() {
        this.audit.touchOnUpdate();
    }

    public void associateService(final Service service) {
        applyServiceState(CalendarEventServiceOps.associate(this, List.of(service)));
    }

    public void associateServices(final List<Service> services) {
        applyServiceState(CalendarEventServiceOps.associate(this, services));
    }

    public boolean enrichServices(final List<Service> services) {
        final CalendarEventServiceState state = CalendarEventServiceOps.enrich(this, services);
        applyServiceState(state);
        return state.changed();
    }

    public void clearServiceAssociation() {
        applyServiceState(CalendarEventServiceState.empty());
    }

    public boolean isGoogleOrigin() {
        return source == CalendarEventSource.GOOGLE && googleEventId != null && !googleEventId.isBlank();
    }

    public boolean hasPaymentType(final PaymentType type) {
        return paymentType == type;
    }

    public String getServiceDescriptionSnapshot() {
        return snapshotView().getDescription();
    }

    public BigDecimal getServiceValueSnapshot() {
        return snapshotView().getTotalValue();
    }

    public BigDecimal getServiceValueOrZero() {
        return snapshotView().totalValueOrZero();
    }

    public boolean hasServiceSnapshot(final String description, final BigDecimal totalValue) {
        final BigDecimal currentTotal = getServiceValueSnapshot();
        return java.util.Objects.equals(getServiceDescriptionSnapshot(), description)
                && ((currentTotal == null && totalValue == null)
                || (currentTotal != null
                && totalValue != null
                && currentTotal.compareTo(totalValue) == 0));
    }

    public boolean hasAnyServiceAssociationData() {
        return snapshotView().getDescription() != null || snapshotView().getTotalValue() != null;
    }

    public Long getPrimaryServiceId() {
        return service != null ? service.getId() : null;
    }

    public String getPrimaryServiceNormalizedDescription() {
        return service != null ? service.getNormalizedDescription() : null;
    }

    public CalendarEventServiceSnapshot getSnapshot() {
        return snapshotView();
    }

    public CalendarEventReadModel toReadModel(final BigDecimal paidAmount) {
        final CalendarEventServiceSnapshot snapshotView = snapshotView();
        return new CalendarEventReadModel(
                eventId,
                googleEventId,
                label.getTitle(),
                timing.startText(),
                timing.endText(),
                identified,
                snapshotView.getDescription(),
                snapshotView.totalValueOrZero(),
                paymentType != null ? paymentType.name() : null,
                paidAmount
        );
    }

    public void updateFromGoogle(final String title,
                                 final String normalizedTitle,
                                 final Instant eventStart,
                                 final Instant eventEnd) {
        this.label.update(title, normalizedTitle);
        this.timing.update(eventStart, eventEnd);
    }

    public void markIdentified(final boolean identified) {
        this.identified = identified;
    }

    public void replacePayments(final List<CalendarEventPayment> payments) {
        this.payments.clear();
        if (payments != null) {
            this.payments.addAll(payments);
        }
    }

    public void clearPayments() {
        this.payments.clear();
    }

    private CalendarEventServiceSnapshot snapshotView() {
        return snapshot != null ? snapshot : CalendarEventServiceSnapshot.empty();
    }

    private void applyServiceState(final CalendarEventServiceState state) {
        this.service = state.primaryService();
        this.snapshot = state.snapshot();
        this.identified = state.identified();
        this.serviceLinks.clear();
        this.serviceLinks.addAll(state.serviceLinks());
    }
}
