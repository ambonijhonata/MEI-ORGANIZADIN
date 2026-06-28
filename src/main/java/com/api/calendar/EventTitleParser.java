package com.api.calendar;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class EventTitleParser {

    private static final char CLIENT_SEP = '-';
    private static final char SERVICE_SEP = '+';
    private static final char SPACE = ' ';
    private static final String EMPTY = "";
    private static final Pattern DEFAULT_SUFFIX_RX = Pattern.compile("\\(([^()]*)\\)\\s*$");
    private static final Map<String, PaymentType> DEFAULT_PAY_TYPES = Map.of(
            "dinheiro", PaymentType.DINHEIRO,
            "debito", PaymentType.DEBITO,
            "credito", PaymentType.CREDITO,
            "pix", PaymentType.PIX
    );
    private static final ParsedTitle EMPTY_RESULT = new ParsedTitle(null, List.of(), null);

    private final Pattern suffixRx;
    private final Map<String, PaymentType> payTypes;

    public EventTitleParser() {
        this.suffixRx = DEFAULT_SUFFIX_RX;
        this.payTypes = DEFAULT_PAY_TYPES;
    }

    public ParsedTitle parse(final String title) {
        ParsedTitle parsedTitle = EMPTY_RESULT;
        if (title != null && !title.isBlank()) {
            final ParsedSuffix parsedSuffix = extractSuffix(title.trim());
            final String titleBody = parsedSuffix.titleBody();
            final PaymentType payType = parsedSuffix.payType();
            final int separatorIndex = titleBody.indexOf(CLIENT_SEP);
            if (separatorIndex < 0) {
                if (titleBody.isBlank()) {
                    parsedTitle = new ParsedTitle(null, List.of(), payType);
                } else {
                    parsedTitle = new ParsedTitle(null, List.of(titleBody), payType);
                }
            } else {
                final String rawClient = titleBody.substring(0, separatorIndex).trim();
                final String clientName = rawClient.isEmpty() ? null : rawClient;
                final String servicesPart = titleBody.substring(separatorIndex + 1).trim();
                parsedTitle = new ParsedTitle(clientName, splitServices(servicesPart), payType);
            }
        }
        return parsedTitle;
    }

    private List<String> splitServices(final String servicesPart) {
        List<String> serviceNames = List.of();
        if (!servicesPart.isEmpty()) {
            final List<String> items = new ArrayList<>();
            int tokenStartIndex = 0;
            for (int charIndex = 0; charIndex < servicesPart.length(); charIndex++) {
                if (servicesPart.charAt(charIndex) == SERVICE_SEP) {
                    final String token = servicesPart.substring(tokenStartIndex, charIndex).trim();
                    if (!token.isEmpty()) {
                        items.add(token);
                    }
                    tokenStartIndex = charIndex + 1;
                }
            }
            final String token = servicesPart.substring(tokenStartIndex).trim();
            if (!token.isEmpty()) {
                items.add(token);
            }
            serviceNames = items;
        }
        return serviceNames;
    }

    private ParsedSuffix extractSuffix(final String title) {
        ParsedSuffix parsedSuffix = new ParsedSuffix(title, null);
        final Matcher matcher = suffixRx.matcher(title);
        if (matcher.find()) {
            final String payToken = matcher.group(1);
            final String payKey = normalizePay(payToken);
            final PaymentType payType = payTypes.get(payKey);
            final String titleBody = title.substring(0, matcher.start()).trim();
            parsedSuffix = new ParsedSuffix(titleBody, payType);
        }
        return parsedSuffix;
    }

    private String normalizePay(final String rawValue) {
        String normalizedPay = EMPTY;
        if (rawValue != null) {
            final String trimmed = rawValue.trim();
            if (!trimmed.isEmpty()) {
                final String compact = compactSpaces(trimmed).toLowerCase(Locale.ROOT);
                final String normalized = Normalizer.normalize(compact, Normalizer.Form.NFD);
                final StringBuilder chars = new StringBuilder(normalized.length());
                for (int charIndex = 0; charIndex < normalized.length(); charIndex++) {
                    final char current = normalized.charAt(charIndex);
                    if (current < '\u0300' || current > '\u036F') {
                        chars.append(current);
                    }
                }
                normalizedPay = chars.toString();
            }
        }
        return normalizedPay;
    }

    private String compactSpaces(final String value) {
        final StringBuilder compact = new StringBuilder(value.length());
        boolean prevWs = false;
        for (int charIndex = 0; charIndex < value.length(); charIndex++) {
            final char current = value.charAt(charIndex);
            if (Character.isWhitespace(current)) {
                if (!prevWs) {
                    compact.append(SPACE);
                }
            } else {
                compact.append(current);
            }
            prevWs = Character.isWhitespace(current);
        }
        return compact.toString();
    }

    public record ParsedTitle(String clientName, List<String> serviceNames, PaymentType paymentType) {
        public boolean hasClient() {
            return clientName != null && !clientName.isBlank();
        }
    }

    private record ParsedSuffix(String titleBody, PaymentType payType) {}
}
