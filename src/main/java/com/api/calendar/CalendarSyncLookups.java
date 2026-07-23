package com.api.calendar;

import com.api.client.Client;
import com.api.servicecatalog.Service;
import java.util.Map;

public record CalendarSyncLookups(
        Map<String, Client> clientsByName,
        Map<String, Service> servicesByName
) {
}
