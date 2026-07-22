package com.api.google;

import java.util.List;
import java.util.Map;

final class GoogleApiErrorSummaryAdapter {

    private static final String KEY_REASON = "reason";

    private final String message;
    private final String reason;

    public GoogleApiErrorSummaryAdapter(final Object messageValue, final Object errorsValue) {
        this.message = normalizeText(messageValue);
        this.reason = extractReason(errorsValue);
    }

    public GoogleApiErrorMapper.ErrorSummary toSummary() {
        return new GoogleApiErrorMapper.ErrorSummary(message, reason);
    }

    private String extractReason(final Object errorsValue) {
        final String extractedReason;
        if (errorsValue instanceof List<?> errors && !errors.isEmpty()) {
            final Object firstError = errors.getFirst();
            if (firstError instanceof Map<?, ?> errorData) {
                extractedReason = normalizeText(errorData.get(KEY_REASON));
            } else {
                extractedReason = null;
            }
        } else {
            extractedReason = null;
        }
        return extractedReason;
    }

    private String normalizeText(final Object value) {
        final String normalizedText;
        if (value instanceof String textValue && !textValue.isBlank()) {
            normalizedText = textValue;
        } else {
            normalizedText = null;
        }
        return normalizedText;
    }
}
