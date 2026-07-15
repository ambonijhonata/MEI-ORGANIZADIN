package com.api.servicecatalog;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

@Component
public class ServiceDescriptionNormalizer {
    private final Locale locale;

    public ServiceDescriptionNormalizer() {
        this.locale = Locale.ROOT;
    }

    public String normalize(final String description) {
        String normalized = "";
        if (description != null) {
            final String trimmed = description.trim();
            if (!trimmed.isEmpty()) {
                final String collapsedSpaces = collapseWhitespace(trimmed);
                final String lowered = collapsedSpaces.toLowerCase(locale);
                final String normalizedNfd = Normalizer.normalize(lowered, Normalizer.Form.NFD);
                normalized = removeCombiningDiacriticalMarks(normalizedNfd);
            }
        }
        return normalized;
    }

    private String collapseWhitespace(final String value) {
        final StringBuilder normalizedText = new StringBuilder(value.length());
        boolean lastWasBlank = false;

        for (int charIndex = 0; charIndex < value.length(); charIndex++) {
            final char current = value.charAt(charIndex);
            if (isRegexWhitespace(current)) {
                if (!lastWasBlank) {
                    normalizedText.append(' ');
                    lastWasBlank = true;
                }
            } else {
                normalizedText.append(current);
                lastWasBlank = false;
            }
        }

        return normalizedText.toString();
    }

    private String removeCombiningDiacriticalMarks(final String value) {
        final StringBuilder normalizedText = new StringBuilder(value.length());
        for (int charIndex = 0; charIndex < value.length(); charIndex++) {
            final char current = value.charAt(charIndex);
            if (current < '\u0300' || current > '\u036F') {
                normalizedText.append(current);
            }
        }
        return normalizedText.toString();
    }

    private boolean isRegexWhitespace(final char value) {
        return value == ' '
                || value == '\t'
                || value == '\n'
                || value == '\u000B'
                || value == '\f'
                || value == '\r';
    }
}
