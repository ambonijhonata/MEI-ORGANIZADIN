package com.api.google;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import java.io.IOException;
import java.util.Map;

final class GoogleApiErrorMapper {

    private static final String KEY_ERRORS = "errors";
    private static final String KEY_MESSAGE = "message";
    private static final int STATUS_GONE = 410;
    private static final int STATUS_UNAUTH = 401;
    private static final int STATUS_FORBIDDEN = 403;
    private static final String EXPIRED_MSG = "Sync token expired, full resync required";
    private static final String UNKNOWN_API_MSG = "Unknown Google API error";

    private GoogleApiErrorMapper() {
    }

    public static IOException map(final IOException exception) {
        IOException mapped = exception;
        if (exception instanceof GoogleJsonResponseException googleException) {
            final Map<String, Object> details = googleException.getDetails();
            final Object messageValue = details == null ? null : details.get(KEY_MESSAGE);
            final Object errorsValue = details == null ? null : details.get(KEY_ERRORS);
            final String message = buildMessage(new GoogleApiErrorSummaryAdapter(messageValue, errorsValue), googleException);
            final int status = googleException.getStatusCode();
            mapped = googleException;
            if (status == STATUS_GONE) {
                mapped = new GoogleCalendarClient.SyncTokenExpiredException(EXPIRED_MSG, googleException);
            } else if (status == STATUS_UNAUTH) {
                mapped = new GoogleCalendarClient.OAuthRevokedException(message, googleException);
            } else if (status == STATUS_FORBIDDEN) {
                mapped = new GoogleCalendarClient.GoogleApiForbiddenException(message, googleException);
            }
        }
        return mapped;
    }

    private static String buildMessage(
            final GoogleApiErrorSummaryAdapter summaryAdapter,
            final GoogleJsonResponseException exception) {
        final ErrorSummary summary = summaryAdapter.toSummary();
        final String detailMsg = detailMessage(summary);
        final String fallbackMsg = fallbackMessage(summary, exception);
        final String message;
        if (detailMsg != null) {
            message = detailMsg;
        } else if (fallbackMsg != null) {
            message = fallbackMsg;
        } else {
            message = UNKNOWN_API_MSG;
        }
        return message;
    }

    private static String detailMessage(final ErrorSummary summary) {
        final String message;
        if (summary.hasMessage() && summary.hasReason()) {
            message = summary.message() + " (reason: " + summary.reason() + ")";
        } else if (summary.hasMessage()) {
            message = summary.message();
        } else {
            message = null;
        }
        return message;
    }

    private static String fallbackMessage(final ErrorSummary summary, final GoogleJsonResponseException exception) {
        final String message;
        if (summary.hasMessage()) {
            message = null;
        } else {
            message = firstNonBlank(exception.getContent(), exception.getStatusMessage());
        }
        return message;
    }

    private static String firstNonBlank(final String first, final String second) {
        final String left = blankToNull(first);
        final String right = blankToNull(second);
        return left != null ? left : right;
    }

    private static String blankToNull(final String value) {
        final String normalized;
        if (value == null || value.isBlank()) {
            normalized = null;
        } else {
            normalized = value;
        }
        return normalized;
    }

    public record ErrorSummary(String message, String reason) {
        private boolean hasMessage() {
            return message != null;
        }

        private boolean hasReason() {
            return reason != null;
        }
    }
}
