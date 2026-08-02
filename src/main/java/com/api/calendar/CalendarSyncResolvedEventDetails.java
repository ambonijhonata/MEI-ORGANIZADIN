package com.api.calendar;

import com.api.client.Client;
import com.api.servicecatalog.Service;

import java.util.List;

public record CalendarSyncResolvedEventDetails(
        String normalizedTitle,
        EventTitleParser.ParsedTitle parsedTitle,
        Client resolvedClient,
        List<Service> matchedServices
) {
}
