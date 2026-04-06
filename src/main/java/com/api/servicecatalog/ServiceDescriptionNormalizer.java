package com.api.servicecatalog;

import org.springframework.stereotype.Component;

import java.text.Normalizer;

@Component
public class ServiceDescriptionNormalizer {

    public String normalize(String description) {
        if (description == null) {
            return "";
        }

        String trimmed = description.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        String collapsedSpaces = collapseWhitespace(trimmed);
        String lowered = collapsedSpaces.toLowerCase();
        String nfd = Normalizer.normalize(lowered, Normalizer.Form.NFD);
        return removeCombiningDiacriticalMarks(nfd);
    }

    private String collapseWhitespace(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        boolean previousWasWhitespace = false;

        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
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

    private String removeCombiningDiacriticalMarks(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current < '\u0300' || current > '\u036F') {
                sb.append(current);
            }
        }
        return sb.toString();
    }

    private boolean isRegexWhitespace(char value) {
        return value == ' '
                || value == '\t'
                || value == '\n'
                || value == '\u000B'
                || value == '\f'
                || value == '\r';
    }
}
