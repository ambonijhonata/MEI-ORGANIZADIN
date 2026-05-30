package com.api.servicecatalog;

import org.springframework.stereotype.Component;

import java.text.Normalizer;

@SuppressWarnings({"PMD.AtLeastOneConstructor", "PMD.LongVariable", "PMD.OnlyOneReturn", "PMD.ShortVariable", "PMD.UseLocaleWithCaseConversions"})
@Component
public class ServiceDescriptionNormalizer {

    public String normalize(final String description) {
        if (description == null) {
            return "";
        }

        final String trimmed = description.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        final String collapsedSpaces = collapseWhitespace(trimmed);
        final String lowered = collapsedSpaces.toLowerCase();
        final String nfd = Normalizer.normalize(lowered, Normalizer.Form.NFD);
        return removeCombiningDiacriticalMarks(nfd);
    }

    private String collapseWhitespace(final String value) {
        final StringBuilder sb = new StringBuilder(value.length());
        boolean previousWasWhitespace = false;

        for (int i = 0; i < value.length(); i++) {
            final char current = value.charAt(i);
            if (isRegexWhitespace(current)) {
                if (!previousWasWhitespace) {
                    sb.append(' ');
                    previousWasWhitespace = true;
                }
            } else {
                sb.append(current);
                previousWasWhitespace = false;
            }
        }

        return sb.toString();
    }

    private String removeCombiningDiacriticalMarks(final String value) {
        final StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            final char current = value.charAt(i);
            if (current < '\u0300' || current > '\u036F') {
                sb.append(current);
            }
        }
        return sb.toString();
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
