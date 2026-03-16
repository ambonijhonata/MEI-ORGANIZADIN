package com.api.calendar;

import com.api.client.Client;
import com.api.client.ClientService;
import com.api.common.IntegrationRevokedException;
import com.api.google.GoogleCalendarClient;
import com.api.servicecatalog.Service;
import com.api.servicecatalog.ServiceDescriptionNormalizer;
import com.api.user.User;
import com.api.user.UserRepository;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class CalendarSyncService {

    private static final Logger log = LoggerFactory.getLogger(CalendarSyncService.class);

    private final GoogleCalendarClient googleCalendarClient;
    private final CalendarEventRepository calendarEventRepository;
    private final SyncStateRepository syncStateRepository;
    private final CalendarEventServiceMatcher matcher;
    private final ServiceDescriptionNormalizer normalizer;
    private final UserRepository userRepository;
    private final EventTitleParser titleParser;
    private final ClientService clientService;

    public CalendarSyncService(GoogleCalendarClient googleCalendarClient,
                                CalendarEventRepository calendarEventRepository,
                                SyncStateRepository syncStateRepository,
                                CalendarEventServiceMatcher matcher,
                                ServiceDescriptionNormalizer normalizer,
                                UserRepository userRepository,
                                EventTitleParser titleParser,
                                ClientService clientService) {
        this.googleCalendarClient = googleCalendarClient;
        this.calendarEventRepository = calendarEventRepository;
        this.syncStateRepository = syncStateRepository;
        this.matcher = matcher;
        this.normalizer = normalizer;
        this.userRepository = userRepository;
        this.titleParser = titleParser;
        this.clientService = clientService;
    }

    @Transactional
    public SyncResult synchronize(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        SyncState syncState = syncStateRepository.findByUserId(userId)
                .orElseGet(() -> syncStateRepository.save(new SyncState(user)));

        if (syncState.getStatus() == SyncStatus.REAUTH_REQUIRED) {
            throw new IntegrationRevokedException("Google integration requires re-authentication");
        }

        syncState.setStatus(SyncStatus.SYNCING);
        syncStateRepository.save(syncState);

        try {
            return performSync(userId, user, syncState);
        } catch (GoogleCalendarClient.OAuthRevokedException e) {
            syncState.markReauthRequired(e.getMessage());
            syncStateRepository.save(syncState);
            throw new IntegrationRevokedException("Google OAuth token has been revoked");
        } catch (IOException e) {
            syncState.markFailed("IO_ERROR", e.getMessage());
            syncStateRepository.save(syncState);
            throw new RuntimeException("Sync failed: " + e.getMessage(), e);
        }
    }

    private SyncResult performSync(Long userId, User user, SyncState syncState) throws IOException {
        String existingSyncToken = syncState.getSyncToken();
        int created = 0, updated = 0, deleted = 0;

        try {
            GoogleCalendarClient.CalendarSyncResult result =
                    googleCalendarClient.fetchEvents(userId, existingSyncToken);

            for (Event event : result.events()) {
                if (isDeletedEvent(event)) {
                    deleteLocalEvent(userId, event.getId());
                    deleted++;
                } else {
                    boolean isNew = processEvent(userId, user, event);
                    if (isNew) created++;
                    else updated++;
                }
            }

            syncState.markSynced(result.nextSyncToken());
            syncStateRepository.save(syncState);

            return new SyncResult(created, updated, deleted);

        } catch (GoogleCalendarClient.SyncTokenExpiredException e) {
            log.info("Sync token expired for user {}, performing full resync", userId);
            return performFullResync(userId, user, syncState);
        }
    }

    private SyncResult performFullResync(Long userId, User user, SyncState syncState) throws IOException {
        syncState.setSyncToken(null);
        int created = 0, updated = 0;

        GoogleCalendarClient.CalendarSyncResult result =
                googleCalendarClient.fetchEvents(userId, null);

        for (Event event : result.events()) {
            if (!isDeletedEvent(event)) {
                boolean isNew = processEvent(userId, user, event);
                if (isNew) created++;
                else updated++;
            }
        }

        syncState.markSynced(result.nextSyncToken());
        syncStateRepository.save(syncState);

        return new SyncResult(created, updated, 0);
    }

    private boolean processEvent(Long userId, User user, Event googleEvent) {
        String googleEventId = googleEvent.getId();
        String title = googleEvent.getSummary();
        String normalizedTitle = normalizer.normalize(title);
        Instant eventStart = extractInstant(googleEvent.getStart());
        Instant eventEnd = extractInstant(googleEvent.getEnd());

        Optional<CalendarEvent> existing = calendarEventRepository.findByUserIdAndGoogleEventId(userId, googleEventId);

        CalendarEvent calendarEvent;
        boolean isNew;
        if (existing.isPresent()) {
            calendarEvent = existing.get();
            calendarEvent.updateFromGoogle(title, normalizedTitle, eventStart, eventEnd);
            isNew = false;
        } else {
            calendarEvent = new CalendarEvent(user, googleEventId, title, normalizedTitle, eventStart, eventEnd);
            isNew = true;
        }

        // Parse title: "{client} - {service1} + {service2} + ..."
        EventTitleParser.ParsedTitle parsed = titleParser.parse(title);

        // Resolve client
        if (parsed.hasClient()) {
            Client client = clientService.findOrCreateByName(userId, user, parsed.clientName());
            calendarEvent.setClient(client);
        }

        // Match services
        List<Service> matchedServices = new ArrayList<>();
        for (String serviceName : parsed.serviceNames()) {
            matcher.matchService(userId, serviceName).ifPresent(matchedServices::add);
        }

        if (!matchedServices.isEmpty()) {
            calendarEvent.associateServices(matchedServices);
        } else {
            calendarEvent.clearServiceAssociation();
        }

        calendarEventRepository.save(calendarEvent);
        return isNew;
    }

    private void deleteLocalEvent(Long userId, String googleEventId) {
        calendarEventRepository.findByUserIdAndGoogleEventId(userId, googleEventId)
                .ifPresent(calendarEventRepository::delete);
    }

    private boolean isDeletedEvent(Event event) {
        return "cancelled".equals(event.getStatus());
    }

    private Instant extractInstant(EventDateTime eventDateTime) {
        if (eventDateTime == null) return Instant.now();
        if (eventDateTime.getDateTime() != null) {
            return Instant.ofEpochMilli(eventDateTime.getDateTime().getValue());
        }
        if (eventDateTime.getDate() != null) {
            return Instant.ofEpochMilli(eventDateTime.getDate().getValue());
        }
        return Instant.now();
    }

    public record SyncResult(int created, int updated, int deleted) {}
}
