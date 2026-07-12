package com.api.google;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import java.io.IOException;

@SuppressWarnings("PMD.LooseCoupling")
final class GoogleApiErrorMapper {

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
            final String message = buildMessage(googleException);
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

    private static String buildMessage(final GoogleJsonResponseException exception) {
        final ErrorSummary summary = summarize(exception.getDetails());
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

    private static ErrorSummary summarize(final GoogleJsonError details) {
        final ErrorSummary summary;
        if (details == null) {
            summary = new ErrorSummary(null, null);
        } else {
            final String message = blankToNull(details.getMessage());
            final String reason;
            if (details.getErrors() == null || details.getErrors().isEmpty()) {
                reason = null;
            } else {
                reason = blankToNull(details.getErrors().get(0).getReason());
            }
            summary = new ErrorSummary(message, reason);
        }
        return summary;
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

    private record ErrorSummary(String message, String reason) {
        private boolean hasMessage() {
            return message != null;
        }

        private boolean hasReason() {
            return reason != null;
        }
    }
}
