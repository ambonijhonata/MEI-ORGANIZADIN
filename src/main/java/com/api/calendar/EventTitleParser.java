package com.api.calendar;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class EventTitleParser {

    public ParsedTitle parse(String title) {
        if (title == null || title.isBlank()) {
            return new ParsedTitle(null, List.of());
        }

        int separatorIndex = title.indexOf(" - ");
        if (separatorIndex < 0) {
            // No client separator: treat entire title as a single service (backward compatibility)
            return new ParsedTitle(null, List.of(title.trim()));
        }

        String clientName = title.substring(0, separatorIndex).trim();
        String servicesPart = title.substring(separatorIndex + 3).trim();

        if (clientName.isEmpty()) {
            clientName = null;
        }

        List<String> serviceNames = Arrays.stream(servicesPart.split("\\+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        return new ParsedTitle(clientName, serviceNames);
    }

    public record ParsedTitle(String clientName, List<String> serviceNames) {
        public boolean hasClient() {
            return clientName != null && !clientName.isBlank();
        }
    }
}
