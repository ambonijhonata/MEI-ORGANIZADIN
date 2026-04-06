package com.api.calendar;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
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

        List<String> serviceNames = splitServices(servicesPart);

        return new ParsedTitle(clientName, serviceNames);
    }

    private List<String> splitServices(String servicesPart) {
        if (servicesPart.isEmpty()) {
            return List.of();
        }

        List<String> serviceNames = new ArrayList<>();
        int tokenStart = 0;

        for (int i = 0; i < servicesPart.length(); i++) {
            if (servicesPart.charAt(i) == '+') {
                addTrimmedToken(serviceNames, servicesPart, tokenStart, i);
                tokenStart = i + 1;
            }
        }

        addTrimmedToken(serviceNames, servicesPart, tokenStart, servicesPart.length());
        return serviceNames;
    }

    private void addTrimmedToken(List<String> serviceNames, String source, int startInclusive, int endExclusive) {
        if (startInclusive >= endExclusive) {
            return;
        }
        String token = source.substring(startInclusive, endExclusive).trim();
        if (!token.isEmpty()) {
            serviceNames.add(token);
        }
    }

    public record ParsedTitle(String clientName, List<String> serviceNames) {
        public boolean hasClient() {
            return clientName != null && !clientName.isBlank();
        }
    }
}
