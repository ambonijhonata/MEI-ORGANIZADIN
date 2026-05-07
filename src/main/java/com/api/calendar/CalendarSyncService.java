package com.api.calendar;

import com.api.client.Client;
import com.api.client.ClientService;
import com.api.common.GoogleApiAccessDeniedException;
import com.api.common.IntegrationRevokedException;
import com.api.google.GoogleCalendarClient;
import com.api.servicecatalog.Service;
import com.api.servicecatalog.ServiceDescriptionNormalizer;
import com.api.user.User;
import com.api.user.UserRepository;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
public class CalendarSyncService {

    private static final Logger log = LoggerFactory.getLogger(CalendarSyncService.class);
    private static final int DEFAULT_BATCH_SIZE = 200;
    private static final int LARGE_INCREMENTAL_LOOKUP_THRESHOLD = 5000;
    private static final SyncMutations EMPTY_MUTATIONS = new SyncMutations(List.of(), List.of(), 0, 0, 0);

    private final GoogleCalendarClient googleCalendarClient;
    private final CalendarEventRepository calendarEventRepository;
    private final CalendarEventPaymentRepository calendarEventPaymentRepository;
    private final CalendarEventServiceLinkRepository calendarEventServiceLinkRepository;
    private final SyncStateRepository syncStateRepository;
    private final CalendarEventServiceMatcher matcher;
    private final ServiceDescriptionNormalizer normalizer;
    private final UserRepository userRepository;
    private final EventTitleParser titleParser;
    private final ClientService clientService;
    private final int batchSize;
    private final boolean batchClearEnabled;
    private final int batchFlushEveryChunks;
    private final ConcurrentMap<Long, ReentrantLock> userSyncLocks = new ConcurrentHashMap<>();
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    CalendarSyncService(GoogleCalendarClient googleCalendarClient,
                        CalendarEventRepository calendarEventRepository,
                        SyncStateRepository syncStateRepository,
                        CalendarEventServiceMatcher matcher,
                        ServiceDescriptionNormalizer normalizer,
                        UserRepository userRepository,
                        EventTitleParser titleParser,
                        ClientService clientService) {
        this(googleCalendarClient, calendarEventRepository, syncStateRepository, matcher, normalizer,
                userRepository, titleParser, clientService, null, null);
    }

    CalendarSyncService(GoogleCalendarClient googleCalendarClient,
                        CalendarEventRepository calendarEventRepository,
                        SyncStateRepository syncStateRepository,
                        CalendarEventServiceMatcher matcher,
                        ServiceDescriptionNormalizer normalizer,
                        UserRepository userRepository,
                        EventTitleParser titleParser,
                        ClientService clientService,
                        CalendarEventPaymentRepository calendarEventPaymentRepository,
                        CalendarEventServiceLinkRepository calendarEventServiceLinkRepository) {
        this(googleCalendarClient, calendarEventRepository, syncStateRepository, matcher, normalizer,
                userRepository, titleParser, clientService, calendarEventPaymentRepository,
                calendarEventServiceLinkRepository, DEFAULT_BATCH_SIZE, false, 1);
    }

    @Autowired
    public CalendarSyncService(GoogleCalendarClient googleCalendarClient,
                               CalendarEventRepository calendarEventRepository,
                               SyncStateRepository syncStateRepository,
                               CalendarEventServiceMatcher matcher,
                               ServiceDescriptionNormalizer normalizer,
                               UserRepository userRepository,
                               EventTitleParser titleParser,
                               ClientService clientService,
                               CalendarEventPaymentRepository calendarEventPaymentRepository,
                               CalendarEventServiceLinkRepository calendarEventServiceLinkRepository,
                               @Value("${calendar.sync.batch-size:" + DEFAULT_BATCH_SIZE + "}") int batchSize,
                               @Value("${calendar.sync.batch-clear-enabled:false}") boolean batchClearEnabled,
                               @Value("${calendar.sync.batch-flush-every-chunks:1}") int batchFlushEveryChunks) {
        this.googleCalendarClient = googleCalendarClient;
        this.calendarEventRepository = calendarEventRepository;
        this.calendarEventPaymentRepository = calendarEventPaymentRepository;
        this.calendarEventServiceLinkRepository = calendarEventServiceLinkRepository;
        this.syncStateRepository = syncStateRepository;
        this.matcher = matcher;
        this.normalizer = normalizer;
        this.userRepository = userRepository;
        this.titleParser = titleParser;
        this.clientService = clientService;
        this.batchSize = batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
        this.batchClearEnabled = batchClearEnabled;
        this.batchFlushEveryChunks = batchFlushEveryChunks <= 0 ? 1 : batchFlushEveryChunks;
    }

    @Autowired(required = false)
    void configureTransactionTemplate(PlatformTransactionManager platformTransactionManager) {
        if (platformTransactionManager != null) {
            this.transactionTemplate = new TransactionTemplate(platformTransactionManager);
        }
    }

    public SyncResult synchronize(Long userId) {
        return synchronize(userId, null);
    }

    public SyncResult synchronize(Long userId, LocalDate startDate) {
        ReentrantLock lock = userSyncLocks.computeIfAbsent(userId, ignored -> new ReentrantLock());
        lock.lock();
        try {
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
                if (startDate != null && !hasSyncToken(syncState)) {
                    return performStartDateSync(userId, user, syncState, startDate);
                }
                return performSync(userId, user, syncState);
            } catch (GoogleCalendarClient.OAuthRevokedException e) {
                syncState.markReauthRequired(e.getMessage());
                syncStateRepository.save(syncState);
                throw new IntegrationRevokedException(e.getMessage());
            } catch (GoogleCalendarClient.GoogleApiForbiddenException e) {
                syncState.markFailed("GOOGLE_API_FORBIDDEN", e.getMessage());
                syncStateRepository.save(syncState);
                throw new GoogleApiAccessDeniedException(e.getMessage());
            } catch (IOException e) {
                syncState.markFailed("IO_ERROR", e.getMessage());
                syncStateRepository.save(syncState);
                throw new RuntimeException("Sync failed: " + e.getMessage(), e);
            } catch (RuntimeException e) {
                syncState.markFailed("INTERNAL_SYNC_ERROR", safeErrorMessage(e));
                syncStateRepository.save(syncState);
                throw e;
            }
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                userSyncLocks.remove(userId, lock);
            }
        }
    }

    private SyncResult performSync(Long userId, User user, SyncState syncState) throws IOException {
        String existingSyncToken = syncState.getSyncToken();
        String tokenBeforeSync = existingSyncToken;
        long totalStartNs = System.nanoTime();
        long googleFetchMs = 0L;
        long dbLookupMs = 0L;
        long processingMs = 0L;
        long dbWriteMs = 0L;
        int created = 0;
        int updated = 0;
        int deleted = 0;
        int eventsReceived = 0;
        boolean fullSync = existingSyncToken == null || existingSyncToken.isBlank();
        String syncMode = fullSync ? "full_no_token" : "incremental";
        Map<String, String> normalizationCache = new HashMap<>();

        try {
            long fetchStartNs = System.nanoTime();
            GoogleCalendarClient.CalendarSyncResult result = googleCalendarClient.fetchEvents(userId, existingSyncToken);
            googleFetchMs = elapsedMs(fetchStartNs);
            eventsReceived = result.events() != null ? result.events().size() : 0;
            SyncExecution execution = executeSyncFlow(
                    userId,
                    user,
                    syncState,
                    result.events(),
                    fullSync,
                    true,
                    tokenBeforeSync,
                    result.nextSyncToken(),
                    syncMode,
                    null,
                    normalizationCache
            );
            dbLookupMs = execution.dbLookupMs();
            processingMs = execution.processingMs();
            dbWriteMs = execution.dbWriteMs();
            created = execution.result().created();
            updated = execution.result().updated();
            deleted = execution.result().deleted();

            logSyncSummary(
                    userId,
                    syncMode,
                    eventsReceived,
                    created,
                    updated,
                    deleted,
                    googleFetchMs,
                    dbLookupMs,
                    processingMs,
                    dbWriteMs,
                    elapsedMs(totalStartNs),
                    false,
                    hasToken(tokenBeforeSync),
                    hasToken(syncState.getSyncToken())
            );

            return execution.result();

        } catch (GoogleCalendarClient.SyncTokenExpiredException e) {
            log.info("calendar_sync_full_resync_fallback userId={} reason=sync_token_expired elapsed_ms={}",
                    userId, elapsedMs(totalStartNs));
            return performFullResync(userId, user, syncState);
        }
    }

    private SyncResult performFullResync(Long userId, User user, SyncState syncState) throws IOException {
        String tokenBeforeSync = syncState.getSyncToken();
        long totalStartNs = System.nanoTime();
        long googleFetchMs = 0L;
        long dbLookupMs = 0L;
        long processingMs = 0L;
        long dbWriteMs = 0L;
        syncState.setSyncToken(null);
        String tokenPreservationCandidate = syncState.getSyncToken();
        int eventsReceived = 0;
        Map<String, String> normalizationCache = new HashMap<>();

        long fetchStartNs = System.nanoTime();
        GoogleCalendarClient.CalendarSyncResult result = googleCalendarClient.fetchEvents(userId, null);
        googleFetchMs = elapsedMs(fetchStartNs);
        eventsReceived = result.events() != null ? result.events().size() : 0;
        SyncExecution execution = executeSyncFlow(
                userId,
                user,
                syncState,
                result.events(),
                true,
                false,
                tokenPreservationCandidate,
                result.nextSyncToken(),
                "full_resync_410",
                null,
                normalizationCache
        );
        dbLookupMs = execution.dbLookupMs();
        processingMs = execution.processingMs();
        dbWriteMs = execution.dbWriteMs();

        logSyncSummary(
                userId,
                "full_resync_410",
                eventsReceived,
                execution.result().created(),
                execution.result().updated(),
                execution.result().deleted(),
                googleFetchMs,
                dbLookupMs,
                processingMs,
                dbWriteMs,
                elapsedMs(totalStartNs),
                true,
                hasToken(tokenBeforeSync),
                hasToken(syncState.getSyncToken())
        );

        return execution.result();
    }

    private SyncResult performStartDateSync(Long userId,
                                            User user,
                                            SyncState syncState,
                                            LocalDate startDate) throws IOException {
        String tokenBeforeSync = syncState.getSyncToken();
        long totalStartNs = System.nanoTime();
        long googleFetchMs = 0L;
        long dbLookupMs = 0L;
        long processingMs = 0L;
        long dbWriteMs = 0L;
        int eventsReceived = 0;
        Map<String, String> normalizationCache = new HashMap<>();

        long fetchStartNs = System.nanoTime();
        GoogleCalendarClient.CalendarSyncResult result = googleCalendarClient.fetchEvents(userId, null, startDate);
        googleFetchMs = elapsedMs(fetchStartNs);
        eventsReceived = result.events() != null ? result.events().size() : 0;
        SyncExecution execution = executeSyncFlow(
                userId,
                user,
                syncState,
                result.events(),
                false,
                false,
                tokenBeforeSync,
                result.nextSyncToken(),
                "start_date_sync",
                startDate,
                normalizationCache
        );
        dbLookupMs = execution.dbLookupMs();
        processingMs = execution.processingMs();
        dbWriteMs = execution.dbWriteMs();

        logSyncSummary(
                userId,
                "start_date_sync",
                eventsReceived,
                execution.result().created(),
                execution.result().updated(),
                execution.result().deleted(),
                googleFetchMs,
                dbLookupMs,
                processingMs,
                dbWriteMs,
                elapsedMs(totalStartNs),
                false,
                hasToken(tokenBeforeSync),
                hasToken(syncState.getSyncToken())
        );

        return execution.result();
    }

    private SyncLookups buildLookups(Long userId) {
        return new SyncLookups(
                copyMap(clientService.clientsByNormalizedName(userId)),
                copyMap(matcher.servicesByNormalizedDescription(userId))
        );
    }

    private SyncExecution executeSyncFlow(Long userId,
                                          User user,
                                          SyncState syncState,
                                          List<Event> googleEvents,
                                          boolean fullSync,
                                          boolean allowDeletes,
                                          String tokenBeforeSync,
                                          String nextSyncToken,
                                          String syncMode,
                                          LocalDate startDate,
                                          Map<String, String> normalizationCache) {
        long lookupStartNs = System.nanoTime();
        SyncLookups lookups = buildLookups(userId);
        long dbLookupMs = elapsedMs(lookupStartNs);

        long processingStartNs = System.nanoTime();
        List<SyncMutations> chunkMutations = buildChunkMutations(
                userId,
                user,
                googleEvents,
                lookups,
                fullSync,
                allowDeletes,
                normalizationCache
        );
        SyncMutations aggregatedMutations = summarizeMutations(chunkMutations);
        SyncMutations reconciledMutations = reconcileScopedMutations(
                userId,
                googleEvents,
                aggregatedMutations,
                fullSync,
                syncMode,
                startDate
        );
        long processingMs = elapsedMs(processingStartNs);

        List<CalendarEvent> reconciliationDeletions = extractAdditionalDeletions(
                aggregatedMutations.deletions(),
                reconciledMutations.deletions()
        );
        long writeStartNs = System.nanoTime();
        executeWithinTransaction(() -> {
            if (!reconciliationDeletions.isEmpty()) {
                persistMutations(new SyncMutations(List.of(), reconciliationDeletions, 0, 0, reconciliationDeletions.size()));
            }
            applySyncStateAfterSuccessfulSync(syncState, tokenBeforeSync, nextSyncToken, userId, syncMode);
            syncStateRepository.save(syncState);
        });
        long dbWriteMs = elapsedMs(writeStartNs);

        return new SyncExecution(
                new SyncResult(
                        reconciledMutations.created(),
                        reconciledMutations.updated(),
                        reconciledMutations.deleted()
                ),
                dbLookupMs,
                processingMs,
                dbWriteMs
        );
    }

    private SyncMutations summarizeMutations(List<SyncMutations> chunkMutations) {
        if (chunkMutations == null || chunkMutations.isEmpty()) {
            return EMPTY_MUTATIONS;
        }

        int created = 0;
        int updated = 0;
        int deleted = 0;
        List<CalendarEvent> deletions = new ArrayList<>();
        for (SyncMutations chunkMutation : chunkMutations) {
            if (chunkMutation == null) {
                continue;
            }
            created += chunkMutation.created();
            updated += chunkMutation.updated();
            deleted += chunkMutation.deleted();
            if (!chunkMutation.deletions().isEmpty()) {
                deletions.addAll(chunkMutation.deletions());
            }
        }
        return new SyncMutations(List.of(), deletions, created, updated, deleted);
    }

    private SyncMutations reconcileScopedMutations(Long userId,
                                                   List<Event> googleEvents,
                                                   SyncMutations mutations,
                                                   boolean fullSync,
                                                   String syncMode,
                                                   LocalDate startDate) {
        List<CalendarEvent> localGoogleBackedEvents;
        if (fullSync) {
            localGoogleBackedEvents = calendarEventRepository.findGoogleBackedByUserId(userId);
        } else if (startDate != null) {
            Instant startDateBoundary = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
            localGoogleBackedEvents = calendarEventRepository
                    .findGoogleBackedByUserIdAndEventStartGreaterThanEqual(userId, startDateBoundary);
        } else {
            return mutations;
        }
        return withScopeReconciliation(mutations, googleEvents, localGoogleBackedEvents, syncMode);
    }

    private Map<String, CalendarEvent> loadExistingEventsByGoogleEventId(Long userId,
                                                                         List<Event> googleEvents,
                                                                         boolean fullSync) {
        Set<String> googleEventIds = extractGoogleEventIds(googleEvents);
        if (googleEventIds.isEmpty()) {
            return Map.of();
        }

        List<CalendarEvent> existingEvents;
        if (fullSync || googleEventIds.size() > LARGE_INCREMENTAL_LOOKUP_THRESHOLD) {
            existingEvents = calendarEventRepository.findAllWithAssociationsByUserId(userId);
            if (existingEvents == null || existingEvents.isEmpty()) {
                existingEvents = calendarEventRepository.findGoogleBackedByUserId(userId);
            }
        } else {
            existingEvents = calendarEventRepository.findWithAssociationsByUserIdAndGoogleEventIdIn(userId, googleEventIds);
            if (existingEvents == null || existingEvents.isEmpty()) {
                existingEvents = calendarEventRepository.findByUserIdAndGoogleEventIdIn(userId, googleEventIds);
            }
        }

        if (existingEvents == null || existingEvents.isEmpty()) {
            return Map.of();
        }

        Map<String, CalendarEvent> existingEventsByGoogleEventId = new HashMap<>();
        for (CalendarEvent event : existingEvents) {
            if (event == null || event.getGoogleEventId() == null || event.getGoogleEventId().isBlank()) {
                continue;
            }
            if (googleEventIds.contains(event.getGoogleEventId())) {
                existingEventsByGoogleEventId.put(event.getGoogleEventId(), event);
            }
        }
        return existingEventsByGoogleEventId;
    }

    private Set<String> extractGoogleEventIds(List<Event> googleEvents) {
        Set<String> googleEventIds = new HashSet<>();
        if (googleEvents == null) {
            return googleEventIds;
        }
        for (Event googleEvent : googleEvents) {
            if (googleEvent != null && googleEvent.getId() != null && !googleEvent.getId().isBlank()) {
                googleEventIds.add(googleEvent.getId());
            }
        }
        return googleEventIds;
    }

    private List<SyncMutations> buildChunkMutations(Long userId,
                                                    User user,
                                                    List<Event> googleEvents,
                                                    SyncLookups lookups,
                                                    boolean fullSync,
                                                    boolean allowDeletes,
                                                    Map<String, String> normalizationCache) {
        if (googleEvents == null || googleEvents.isEmpty()) {
            return List.of();
        }

        int chunkSize = Math.max(1, batchSize);
        List<SyncMutations> chunks = new ArrayList<>();
        for (int i = 0; i < googleEvents.size(); i += chunkSize) {
            int endExclusive = Math.min(i + chunkSize, googleEvents.size());
            List<Event> chunkEvents = googleEvents.subList(i, endExclusive);
            chunks.add(executeWithinTransaction(() -> processChunkMutations(
                    userId,
                    user,
                    chunkEvents,
                    lookups,
                    fullSync,
                    allowDeletes,
                    normalizationCache
            )));
        }
        return chunks;
    }

    private SyncMutations processChunkMutations(Long userId,
                                                User user,
                                                List<Event> googleEvents,
                                                SyncLookups lookups,
                                                boolean fullSync,
                                                boolean allowDeletes,
                                                Map<String, String> normalizationCache) {
        if (googleEvents == null || googleEvents.isEmpty()) {
            return EMPTY_MUTATIONS;
        }

        Map<String, CalendarEvent> existingEventsByGoogleEventId = loadExistingEventsByGoogleEventId(
                userId,
                googleEvents,
                fullSync
        );
        Map<Long, Set<String>> existingServiceIdentitiesByEventId =
                loadServiceIdentityByEventId(existingEventsByGoogleEventId.values());

        List<CalendarEvent> upserts = new ArrayList<>(googleEvents.size());
        List<CalendarEvent> deletions = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int deleted = 0;

        for (Event googleEvent : googleEvents) {
            if (googleEvent == null || googleEvent.getId() == null || googleEvent.getId().isBlank()) {
                continue;
            }

            CalendarEvent existingEvent = existingEventsByGoogleEventId.get(googleEvent.getId());
            if (isDeletedEvent(googleEvent)) {
                if (existingEvent != null) {
                    if (allowDeletes) {
                        deletions.add(existingEvent);
                        deleted++;
                    }
                }
                continue;
            }

            ProcessedEvent processedEvent = processEvent(
                    userId,
                    user,
                    googleEvent,
                    existingEvent,
                    lookups.clientsByNormalizedName(),
                    lookups.servicesByNormalizedDescription(),
                    existingEvent != null
                            ? existingServiceIdentitiesByEventId.getOrDefault(existingEvent.getId(), Set.of())
                            : Set.of(),
                    normalizationCache
            );
            if (processedEvent.shouldPersist()) {
                upserts.add(processedEvent.calendarEvent());
            }
            if (processedEvent.isNew()) {
                created++;
            } else if (processedEvent.shouldPersist()) {
                updated++;
            }
        }

        SyncMutations mutations = new SyncMutations(upserts, deletions, created, updated, deleted);
        persistMutations(mutations);
        return mutations;
    }

    private ProcessedEvent processEvent(Long userId,
                                        User user,
                                        Event googleEvent,
                                        CalendarEvent existingEvent,
                                        Map<String, Client> clientsByNormalizedName,
                                        Map<String, Service> servicesByNormalizedDescription,
                                        Set<String> existingServiceIdentities,
                                        Map<String, String> normalizationCache) {
        String googleEventId = googleEvent.getId();
        String title = googleEvent.getSummary();
        String normalizedTitle = normalizeWithCache(title, normalizationCache);
        Instant eventStart = extractInstant(googleEvent.getStart());
        Instant eventEnd = extractInstant(googleEvent.getEnd());
        EventTitleParser.ParsedTitle parsed = titleParser.parse(title);

        Client resolvedClient = resolveClient(
                userId,
                user,
                parsed,
                clientsByNormalizedName,
                normalizationCache
        );
        List<Service> matchedServices = resolveMatchedServices(
                parsed,
                servicesByNormalizedDescription,
                normalizationCache
        );

        if (existingEvent == null) {
            CalendarEvent calendarEvent = new CalendarEvent(user, googleEventId, title, normalizedTitle, eventStart, eventEnd);
            if (parsed.hasClient()) {
                calendarEvent.setClient(resolvedClient);
            }
            calendarEvent.setPaymentType(parsed.paymentType());
            applyServiceAssociation(calendarEvent, matchedServices);
            return new ProcessedEvent(calendarEvent, true, true);
        }

        boolean coreDataChanged = hasCoreDataChanges(existingEvent, title, normalizedTitle, eventStart, eventEnd);
        boolean clientChanged = parsed.hasClient() && !isEquivalentClient(existingEvent.getClient(), resolvedClient);
        boolean serviceAssociationChanged = hasServiceAssociationChanges(
                existingEvent,
                matchedServices,
                existingServiceIdentities
        );
        boolean paymentTypeChanged = !Objects.equals(existingEvent.getPaymentType(), parsed.paymentType());
        boolean shouldPersist = coreDataChanged || clientChanged || serviceAssociationChanged || paymentTypeChanged;

        if (!shouldPersist) {
            return new ProcessedEvent(existingEvent, false, false);
        }

        if (coreDataChanged) {
            existingEvent.updateFromGoogle(title, normalizedTitle, eventStart, eventEnd);
        }
        if (clientChanged) {
            existingEvent.setClient(resolvedClient);
        }
        if (serviceAssociationChanged) {
            applyServiceAssociation(existingEvent, matchedServices);
        }
        if (paymentTypeChanged) {
            existingEvent.setPaymentType(parsed.paymentType());
        }

        return new ProcessedEvent(existingEvent, false, true);
    }

    private Client resolveClient(Long userId,
                                 User user,
                                 EventTitleParser.ParsedTitle parsed,
                                 Map<String, Client> clientsByNormalizedName,
                                 Map<String, String> normalizationCache) {
        if (!parsed.hasClient()) {
            return null;
        }

        String normalizedClientName = normalizeWithCache(parsed.clientName(), normalizationCache);
        Client client = clientsByNormalizedName.get(normalizedClientName);
        if (client != null) {
            return client;
        }

        client = clientService.findOrCreateByName(userId, user, parsed.clientName());
        clientsByNormalizedName.put(normalizedClientName, client);
        return client;
    }

    private List<Service> resolveMatchedServices(EventTitleParser.ParsedTitle parsed,
                                                 Map<String, Service> servicesByNormalizedDescription,
                                                 Map<String, String> normalizationCache) {
        if (parsed.serviceNames().isEmpty()) {
            return List.of();
        }

        List<Service> matchedServices = new ArrayList<>(parsed.serviceNames().size());
        for (String serviceName : parsed.serviceNames()) {
            String normalizedServiceName = normalizeWithCache(serviceName, normalizationCache);
            Service service = servicesByNormalizedDescription.get(normalizedServiceName);
            if (service != null) {
                matchedServices.add(service);
            }
        }
        return matchedServices;
    }

    private void applyServiceAssociation(CalendarEvent calendarEvent, List<Service> matchedServices) {
        if (!matchedServices.isEmpty()) {
            calendarEvent.associateServices(matchedServices);
        } else {
            calendarEvent.clearServiceAssociation();
        }
    }

    private boolean hasCoreDataChanges(CalendarEvent existingEvent,
                                       String title,
                                       String normalizedTitle,
                                       Instant eventStart,
                                       Instant eventEnd) {
        return !Objects.equals(existingEvent.getTitle(), title)
                || !Objects.equals(existingEvent.getNormalizedTitle(), normalizedTitle)
                || !Objects.equals(existingEvent.getEventStart(), eventStart)
                || !Objects.equals(existingEvent.getEventEnd(), eventEnd);
    }

    private boolean hasServiceAssociationChanges(CalendarEvent existingEvent,
                                                 List<Service> matchedServices,
                                                 Set<String> existingServiceIdentities) {
        Set<String> persistedServiceIdentities = existingServiceIdentities != null
                ? existingServiceIdentities
                : Set.of();

        if (matchedServices.isEmpty()) {
            return hasPersistedAssociation(existingEvent, persistedServiceIdentities);
        }

        if (!existingEvent.isIdentified()) {
            return true;
        }

        Service firstMatched = matchedServices.get(0);

        if (!Objects.equals(existingEvent.getServiceDescriptionSnapshot(), firstMatched.getDescription())) {
            return true;
        }

        if (!sameMoney(existingEvent.getServiceValueSnapshot(), sumValues(matchedServices))) {
            return true;
        }

        if (persistedServiceIdentities.isEmpty()) {
            return false;
        }

        return !persistedServiceIdentities.equals(serviceIdentitySet(matchedServices));
    }

    private boolean hasPersistedAssociation(CalendarEvent existingEvent,
                                            Set<String> existingServiceIdentities) {
        return existingEvent.isIdentified()
                || (existingServiceIdentities != null && !existingServiceIdentities.isEmpty())
                || existingEvent.getServiceDescriptionSnapshot() != null
                || existingEvent.getServiceValueSnapshot() != null;
    }

    private boolean isEquivalentClient(Client existingClient, Client resolvedClient) {
        if (existingClient == resolvedClient) {
            return true;
        }
        if (existingClient == null || resolvedClient == null) {
            return false;
        }
        if (existingClient.getId() != null && resolvedClient.getId() != null) {
            return Objects.equals(existingClient.getId(), resolvedClient.getId());
        }
        return Objects.equals(existingClient.getNormalizedName(), resolvedClient.getNormalizedName())
                && Objects.equals(existingClient.getName(), resolvedClient.getName());
    }

    private Set<String> serviceIdentitySet(List<Service> services) {
        Set<String> identities = new HashSet<>();
        for (Service service : services) {
            identities.add(serviceIdentity(service));
        }
        return identities;
    }

    private String serviceIdentity(Service service) {
        if (service == null) {
            return "none";
        }
        return serviceIdentity(
                service.getId(),
                service.getNormalizedDescription(),
                service.getDescription(),
                service.getValue()
        );
    }

    private String serviceIdentity(Long serviceId,
                                   String serviceNormalizedDescription,
                                   String serviceDescription,
                                   BigDecimal serviceValue) {
        if (serviceId != null) {
            return "id:" + serviceId;
        }
        if (serviceNormalizedDescription != null && !serviceNormalizedDescription.isBlank()) {
            return "normalized:" + serviceNormalizedDescription;
        }
        if (serviceDescription != null && !serviceDescription.isBlank()) {
            return "description:" + serviceDescription;
        }
        if (serviceValue != null) {
            return "value:" + serviceValue.stripTrailingZeros().toPlainString();
        }
        return "none";
    }

    private BigDecimal sumValues(List<Service> services) {
        BigDecimal total = BigDecimal.ZERO;
        for (Service service : services) {
            if (service != null && service.getValue() != null) {
                total = total.add(service.getValue());
            }
        }
        return total;
    }

    private boolean sameMoney(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.compareTo(right) == 0;
    }

    private boolean isNullOrEmpty(List<?> values) {
        return values == null || values.isEmpty();
    }

    private String normalizeWithCache(String rawValue, Map<String, String> normalizationCache) {
        if (rawValue == null) {
            return normalizer.normalize(null);
        }
        return normalizationCache.computeIfAbsent(rawValue, normalizer::normalize);
    }

    private void persistMutations(SyncMutations mutations) {
        if (!mutations.deletions().isEmpty()) {
            Set<Long> deletionEventIds = new HashSet<>();
            for (CalendarEvent deletion : mutations.deletions()) {
                if (deletion != null && deletion.getId() != null) {
                    deletionEventIds.add(deletion.getId());
                }
            }
            if (!deletionEventIds.isEmpty() && calendarEventPaymentRepository != null) {
                calendarEventPaymentRepository.deleteInBulkByCalendarEventIdIn(deletionEventIds);
                // Explicitly flush between payment cleanup and event deletion to keep batched statements isolated.
                calendarEventPaymentRepository.flush();
            }
            calendarEventRepository.deleteAllInBatch(mutations.deletions());
        }
        if (!mutations.upserts().isEmpty()) {
            saveEventsInBatches(mutations.upserts());
        }
    }

    private void executeWithinTransaction(Runnable work) {
        if (transactionTemplate == null) {
            work.run();
            return;
        }
        transactionTemplate.executeWithoutResult(status -> work.run());
    }

    private <T> T executeWithinTransaction(Supplier<T> work) {
        if (transactionTemplate == null) {
            return work.get();
        }
        return transactionTemplate.execute(status -> work.get());
    }

    private void saveEventsInBatches(List<CalendarEvent> eventsToPersist) {
        int chunkSize = Math.max(1, batchSize);
        int chunkCounter = 0;
        for (int i = 0; i < eventsToPersist.size(); i += chunkSize) {
            int endExclusive = Math.min(i + chunkSize, eventsToPersist.size());
            List<CalendarEvent> chunk = eventsToPersist.subList(i, endExclusive);
            calendarEventRepository.saveAll(chunk);
            chunkCounter++;
            boolean shouldFlush = (chunkCounter % batchFlushEveryChunks == 0) || endExclusive == eventsToPersist.size();
            if (shouldFlush) {
                calendarEventRepository.flush();
                if (batchClearEnabled && entityManager != null) {
                    entityManager.clear();
                }
            }
        }
    }

    private Map<Long, Set<String>> loadServiceIdentityByEventId(Iterable<CalendarEvent> events) {
        Map<Long, Set<String>> identitiesByEventId = new HashMap<>();
        if (events == null || calendarEventServiceLinkRepository == null) {
            return identitiesByEventId;
        }

        List<Long> eventIds = new ArrayList<>();
        for (CalendarEvent event : events) {
            if (event == null || event.getId() == null) {
                continue;
            }
            eventIds.add(event.getId());
        }

        if (eventIds.isEmpty()) {
            return identitiesByEventId;
        }

        List<CalendarEventServiceLinkRepository.ServiceIdentityRow> linkedRows =
                calendarEventServiceLinkRepository.findServiceIdentityRowsByCalendarEventIdIn(eventIds);
        if (linkedRows != null) {
            for (CalendarEventServiceLinkRepository.ServiceIdentityRow row : linkedRows) {
                if (row == null || row.getCalendarEventId() == null) {
                    continue;
                }
                identitiesByEventId.computeIfAbsent(row.getCalendarEventId(), ignored -> new HashSet<>())
                        .add(serviceIdentity(
                                row.getServiceId(),
                                row.getServiceNormalizedDescription(),
                                row.getServiceDescription(),
                                row.getServiceValue()
                        ));
            }
        }

        List<CalendarEventRepository.ServiceIdentityRow> legacyRows =
                calendarEventRepository.findLegacyServiceIdentityRowsByCalendarEventIdIn(eventIds);
        if (legacyRows != null) {
            for (CalendarEventRepository.ServiceIdentityRow row : legacyRows) {
                if (row == null || row.getCalendarEventId() == null) {
                    continue;
                }
                identitiesByEventId.computeIfAbsent(row.getCalendarEventId(), ignored -> new HashSet<>())
                        .add(serviceIdentity(
                                row.getServiceId(),
                                row.getServiceNormalizedDescription(),
                                row.getServiceDescription(),
                                row.getServiceValue()
                        ));
            }
        }

        for (Long eventId : eventIds) {
            identitiesByEventId.computeIfAbsent(eventId, ignored -> new HashSet<>());
        }
        return identitiesByEventId;
    }

    private String safeErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unexpected internal error during calendar synchronization";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }

    private List<CalendarEvent> extractAdditionalDeletions(List<CalendarEvent> baseDeletions,
                                                           List<CalendarEvent> reconciledDeletions) {
        if (reconciledDeletions == null || reconciledDeletions.isEmpty()) {
            return List.of();
        }

        Set<String> seenGoogleEventIds = new HashSet<>();
        if (baseDeletions != null) {
            for (CalendarEvent deletion : baseDeletions) {
                if (deletion != null && deletion.getGoogleEventId() != null) {
                    seenGoogleEventIds.add(deletion.getGoogleEventId());
                }
            }
        }

        List<CalendarEvent> additionalDeletions = new ArrayList<>();
        for (CalendarEvent deletion : reconciledDeletions) {
            if (deletion == null || deletion.getGoogleEventId() == null) {
                continue;
            }
            if (!seenGoogleEventIds.contains(deletion.getGoogleEventId())) {
                seenGoogleEventIds.add(deletion.getGoogleEventId());
                additionalDeletions.add(deletion);
            }
        }
        return additionalDeletions;
    }

    private boolean isDeletedEvent(Event event) {
        return "cancelled".equals(event.getStatus());
    }

    private boolean hasSyncToken(SyncState syncState) {
        return syncState.getSyncToken() != null && !syncState.getSyncToken().isBlank();
    }

    private SyncMutations withScopeReconciliation(SyncMutations mutations,
                                                  List<Event> googleEvents,
                                                  List<CalendarEvent> localScopedEvents,
                                                  String mode) {
        if (localScopedEvents == null || localScopedEvents.isEmpty()) {
            return mutations;
        }

        Map<String, CalendarEvent> localScopedByGoogleEventId = new HashMap<>();
        for (CalendarEvent localEvent : localScopedEvents) {
            if (localEvent == null || localEvent.getGoogleEventId() == null || localEvent.getGoogleEventId().isBlank()) {
                continue;
            }
            localScopedByGoogleEventId.put(localEvent.getGoogleEventId(), localEvent);
        }
        if (localScopedByGoogleEventId.isEmpty()) {
            return mutations;
        }

        Set<String> activeGoogleEventIds = new HashSet<>();
        if (googleEvents != null) {
            for (Event googleEvent : googleEvents) {
                if (googleEvent == null || googleEvent.getId() == null || googleEvent.getId().isBlank()) {
                    continue;
                }
                if (!isDeletedEvent(googleEvent)) {
                    activeGoogleEventIds.add(googleEvent.getId());
                }
            }
        }

        List<CalendarEvent> reconciledDeletions = new ArrayList<>(mutations.deletions());
        Set<String> deletionIds = new HashSet<>();
        for (CalendarEvent deletion : mutations.deletions()) {
            if (deletion.getGoogleEventId() != null) {
                deletionIds.add(deletion.getGoogleEventId());
            }
        }

        int reconciledDeleted = mutations.deleted();
        for (Map.Entry<String, CalendarEvent> entry : localScopedByGoogleEventId.entrySet()) {
            String googleEventId = entry.getKey();
            if (!activeGoogleEventIds.contains(googleEventId) && !deletionIds.contains(googleEventId)) {
                reconciledDeletions.add(entry.getValue());
                deletionIds.add(googleEventId);
                reconciledDeleted++;
            }
        }

        if (reconciledDeleted == mutations.deleted()) {
            return mutations;
        }

        int cleanupDeleted = reconciledDeleted - mutations.deleted();
        log.info(
                "calendar_sync_cleanup_summary mode={} cleanup_deleted={} marker_deleted={} total_deleted={} scoped_local_google_events={} active_google_events={}",
                mode,
                cleanupDeleted,
                mutations.deleted(),
                reconciledDeleted,
                localScopedByGoogleEventId.size(),
                activeGoogleEventIds.size()
        );

        return new SyncMutations(
                mutations.upserts(),
                reconciledDeletions,
                mutations.created(),
                mutations.updated(),
                reconciledDeleted
        );
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

    private void logSyncSummary(Long userId,
                                String mode,
                                int eventsReceived,
                                int created,
                                int updated,
                                int deleted,
                                long googleFetchMs,
                                long dbLookupMs,
                                long processingMs,
                                long dbWriteMs,
                                long totalMs,
                                boolean fallbackFromExpiredToken,
                                boolean tokenBeforePresent,
                                boolean tokenAfterPresent) {
        log.info(
                "calendar_sync_summary userId={} mode={} events_received={} created={} updated={} deleted={} google_fetch_ms={} db_lookup_ms={} processing_ms={} db_write_ms={} sync_total_ms={} fallback_from_expired_token={} token_before_present={} token_after_present={}",
                userId,
                mode,
                eventsReceived,
                created,
                updated,
                deleted,
                googleFetchMs,
                dbLookupMs,
                processingMs,
                dbWriteMs,
                totalMs,
                fallbackFromExpiredToken,
                tokenBeforePresent,
                tokenAfterPresent
        );
    }

    private long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }

    private void markSyncedWithoutTouchingToken(SyncState syncState) {
        syncState.setLastSyncAt(Instant.now());
        syncState.setStatus(SyncStatus.SYNCED);
        syncState.setErrorCategory(null);
        syncState.setErrorMessage(null);
    }

    private void applySyncStateAfterSuccessfulSync(SyncState syncState,
                                                   String tokenBeforeSync,
                                                   String nextSyncToken,
                                                   Long userId,
                                                   String mode) {
        if (hasToken(nextSyncToken)) {
            syncState.markSynced(nextSyncToken);
            return;
        }

        if (hasToken(tokenBeforeSync)) {
            markSyncedWithoutTouchingToken(syncState);
            syncState.setSyncToken(tokenBeforeSync);
            log.warn(
                    "calendar_sync_token_missing userId={} mode={} action=preserve_existing_token token_before_present=true",
                    userId,
                    mode
            );
            return;
        }

        syncState.markSynced(null);
        log.warn(
                "calendar_sync_token_missing userId={} mode={} action=keep_token_empty token_before_present=false",
                userId,
                mode
        );
    }

    private boolean hasToken(String token) {
        return token != null && !token.isBlank();
    }

    private <K, V> Map<K, V> copyMap(Map<K, V> source) {
        if (source == null || source.isEmpty()) {
            return new HashMap<>();
        }
        return new HashMap<>(source);
    }

    private record SyncLookups(
            Map<String, Client> clientsByNormalizedName,
            Map<String, Service> servicesByNormalizedDescription
    ) {}

    private record SyncExecution(
            SyncResult result,
            long dbLookupMs,
            long processingMs,
            long dbWriteMs
    ) {}

    private record SyncMutations(
            List<CalendarEvent> upserts,
            List<CalendarEvent> deletions,
            int created,
            int updated,
            int deleted
    ) {}

    private record ProcessedEvent(CalendarEvent calendarEvent, boolean isNew, boolean shouldPersist) {}

    public record SyncResult(int created, int updated, int deleted) {}
}
