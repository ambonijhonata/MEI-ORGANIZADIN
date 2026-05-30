package com.api.calendar;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings({"PMD.AtLeastOneConstructor", "PMD.AvoidLiteralsInIfCondition", "PMD.LongVariable", "PMD.NullAssignment", "PMD.OnlyOneReturn", "PMD.ShortVariable"})
@Component
public class EventTitleParser {

    private static final Pattern PAYMENT_SUFFIX_PATTERN = Pattern.compile("\\(([^()]*)\\)\\s*$");
    private static final Map<String, PaymentType> PAYMENT_TYPES_BY_LABEL = Map.of(
            "dinheiro", PaymentType.DINHEIRO,
            "debito", PaymentType.DEBITO,
            "credito", PaymentType.CREDITO,
            "pix", PaymentType.PIX
    );

    public ParsedTitle parse(final String title) {
        if (title == null || title.isBlank()) {
            return new ParsedTitle(null, List.of(), null);
        }

        final ParsedSuffix parsedSuffix = extractPaymentSuffix(title.trim());
        final String titleWithoutPayment = parsedSuffix.titleWithoutPayment();
        final PaymentType paymentType = parsedSuffix.paymentType();

        final int separatorIndex = titleWithoutPayment.indexOf('-');
        if (separatorIndex < 0) {
            // No client separator: treat entire title as a single service (backward compatibility)
            if (titleWithoutPayment.isBlank()) {
                return new ParsedTitle(null, List.of(), paymentType);
            }
            return new ParsedTitle(null, List.of(titleWithoutPayment), paymentType);
        }

        String clientName = titleWithoutPayment.substring(0, separatorIndex).trim();
        final String servicesPart = titleWithoutPayment.substring(separatorIndex + 1).trim();

        if (clientName.isEmpty()) {
            clientName = null;
        }

        final List<String> serviceNames = splitServices(servicesPart);

        return new ParsedTitle(clientName, serviceNames, paymentType);
    }

    private List<String> splitServices(final String servicesPart) {
        if (servicesPart.isEmpty()) {
            return List.of();
        }

        final List<String> serviceNames = new ArrayList<>();
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

    private void addTrimmedToken(final List<String> serviceNames, final String source, final int startInclusive, final int endExclusive) {
        if (startInclusive >= endExclusive) {
            return;
        }
        final String token = source.substring(startInclusive, endExclusive).trim();
        if (!token.isEmpty()) {
            serviceNames.add(token);
        }
    }

    private ParsedSuffix extractPaymentSuffix(final String title) {
        final Matcher matcher = PAYMENT_SUFFIX_PATTERN.matcher(title);
        if (!matcher.find()) {
            return new ParsedSuffix(title, null);
        }

        final String paymentToken = matcher.group(1);
        final String normalizedPayment = normalizePaymentToken(paymentToken);
        final PaymentType paymentType = PAYMENT_TYPES_BY_LABEL.get(normalizedPayment);
        final String titleWithoutPayment = title.substring(0, matcher.start()).trim();
        return new ParsedSuffix(titleWithoutPayment, paymentType);
    }

    private String normalizePaymentToken(final String rawValue) {
        if (rawValue == null) {
            return "";
        }

        final String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        final String collapsed = collapseWhitespace(trimmed).toLowerCase(Locale.ROOT);
        final String nfd = Normalizer.normalize(collapsed, Normalizer.Form.NFD);
        final StringBuilder sb = new StringBuilder(nfd.length());
        for (int i = 0; i < nfd.length(); i++) {
            final char current = nfd.charAt(i);
            if (current < '\u0300' || current > '\u036F') {
                sb.append(current);
            }
        }
        return sb.toString();
    }

    private String collapseWhitespace(final String value) {
        final StringBuilder sb = new StringBuilder(value.length());
        boolean previousWasWhitespace = false;

        for (int i = 0; i < value.length(); i++) {
            final char current = value.charAt(i);
            if (Character.isWhitespace(current)) {
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

    public record ParsedTitle(String clientName, List<String> serviceNames, PaymentType paymentType) {
        public boolean hasClient() {
            return clientName != null && !clientName.isBlank();
        }
    }

    private record ParsedSuffix(String titleWithoutPayment, PaymentType paymentType) {}
}
