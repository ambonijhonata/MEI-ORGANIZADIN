package com.api.calendar;

import com.api.client.Client;
import com.api.client.ClientService;
import com.api.servicecatalog.Service;
import com.api.servicecatalog.ServiceDescriptionNormalizer;
import com.api.user.ApplicationUser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CalendarSyncLookupResolver {

    private final ClientService clientService;
    private final CalendarEventServiceMatcher matcher;
    private final ServiceDescriptionNormalizer normalizer;
    private final EventTitleParser titleParser;

    public CalendarSyncLookupResolver(final ClientService clientService,
                                      final CalendarEventServiceMatcher matcher,
                                      final ServiceDescriptionNormalizer normalizer,
                                      final EventTitleParser titleParser) {
        this.clientService = clientService;
        this.matcher = matcher;
        this.normalizer = normalizer;
        this.titleParser = titleParser;
    }

    public CalendarSyncLookups buildLookups(final Long userId) {
        return new CalendarSyncLookups(
                copyMap(clientService.listClientsByNormalizedName(userId)),
                copyMap(matcher.servicesByNormalizedDescription(userId))
        );
    }

    public CalendarSyncResolvedEventDetails resolveEventDetails(final Long userId,
                                                                final ApplicationUser user,
                                                                final String title,
                                                                final CalendarSyncLookups lookups,
                                                                final Map<String, String> normCache) {
        final String normalizedTitle = normalizeWithCache(title, normCache);
        final EventTitleParser.ParsedTitle parsedTitle = titleParser.parse(title);
        final Client resolvedClient = resolveClient(
                userId,
                user,
                parsedTitle,
                lookups.clientsByName(),
                normCache
        );
        final List<Service> matchedServices = resolveMatchedServices(
                parsedTitle,
                lookups.servicesByName(),
                normCache
        );
        return new CalendarSyncResolvedEventDetails(
                normalizedTitle,
                parsedTitle,
                resolvedClient,
                matchedServices
        );
    }

    private Client resolveClient(final Long userId,
                                 final ApplicationUser user,
                                 final EventTitleParser.ParsedTitle parsedTitle,
                                 final Map<String, Client> clientsByName,
                                  final Map<String, String> normCache) {
        Client client = null;
        if (parsedTitle.hasClient()) {
            final String clientKey = normalizeWithCache(parsedTitle.clientName(), normCache);
            client = clientsByName.get(clientKey);
            if (client == null) {
                client = clientService.findOrCreateByName(userId, user, parsedTitle.clientName());
                clientsByName.put(clientKey, client);
            }
        }
        return client;
    }

    private List<Service> resolveMatchedServices(final EventTitleParser.ParsedTitle parsedTitle,
                                                 final Map<String, Service> servicesByName,
                                                 final Map<String, String> normCache) {
        List<Service> matchedServices = List.of();
        if (!parsedTitle.serviceNames().isEmpty()) {
            final List<Service> resolvedServices = new ArrayList<>(parsedTitle.serviceNames().size());
            for (final String serviceName : parsedTitle.serviceNames()) {
                final String serviceKey = normalizeWithCache(serviceName, normCache);
                final Service service = servicesByName.get(serviceKey);
                if (service != null) {
                    resolvedServices.add(service);
                }
            }
            matchedServices = resolvedServices;
        }
        return matchedServices;
    }

    private String normalizeWithCache(final String rawValue, final Map<String, String> normCache) {
        final String normalizedValue;
        if (rawValue == null) {
            normalizedValue = normalizer.normalize(null);
        } else {
            normalizedValue = normCache.computeIfAbsent(rawValue, normalizer::normalize);
        }
        return normalizedValue;
    }

    private <K, V> Map<K, V> copyMap(final Map<K, V> source) {
        final Map<K, V> copiedSource;
        if (source == null || source.isEmpty()) {
            copiedSource = new HashMap<>();
        } else {
            copiedSource = new HashMap<>(source);
        }
        return copiedSource;
    }
}
