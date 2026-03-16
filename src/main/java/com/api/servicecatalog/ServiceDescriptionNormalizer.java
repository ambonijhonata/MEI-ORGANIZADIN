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
        String collapsedSpaces = trimmed.replaceAll("\\s+", " ");
        String lowered = collapsedSpaces.toLowerCase();
        String nfd = Normalizer.normalize(lowered, Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
