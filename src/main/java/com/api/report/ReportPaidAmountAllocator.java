package com.api.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

final class ReportPaidAmountAllocator {

    private static final int CURRENCY_SCALE = 2;
    private static final BigDecimal ZERO_AMT = BigDecimal.ZERO.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);

    private ReportPaidAmountAllocator() {
    }

    /* default */ // Package-private so report services can share the same normalization/allocation rules.
    static List<BigDecimal> distribute(final BigDecimal amount, final List<BigDecimal> weights) {
        List<BigDecimal> allocations = List.of();
        if (hasWeights(weights)) {
            allocations = computeAllocations(normalize(amount), weights);
        }
        return allocations;
    }

    /* default */ // Package-private so collaborators in this package keep currency handling consistent.
    static BigDecimal normalize(final BigDecimal amount) {
        BigDecimal normalizedAmt = ZERO_AMT;
        if (amount != null && amount.compareTo(BigDecimal.ZERO) >= 0) {
            normalizedAmt = amount.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
        }
        return normalizedAmt;
    }

    private static boolean hasWeights(final List<BigDecimal> weights) {
        return weights != null && !weights.isEmpty();
    }

    private static List<BigDecimal> computeAllocations(final BigDecimal targetAmt, final List<BigDecimal> weights) {
        List<BigDecimal> allocations = zeroAllocation(weights.size());
        if (targetAmt.compareTo(ZERO_AMT) > 0) {
            final BigDecimal totalWeight = weights.stream()
                    .map(ReportPaidAmountAllocator::normalize)
                    .reduce(ZERO_AMT, BigDecimal::add);
            if (totalWeight.compareTo(ZERO_AMT) > 0) {
                allocations = splitByWeights(targetAmt, weights, totalWeight);
            }
        }
        return allocations;
    }

    private static List<BigDecimal> splitByWeights(final BigDecimal targetAmt,
                                                   final List<BigDecimal> weights,
                                                   final BigDecimal totalWeight) {
        final List<BigDecimal> allocations = new ArrayList<>(weights.size());
        BigDecimal allocatedAmt = ZERO_AMT;
        for (int index = 0; index < weights.size(); index++) {
            final boolean isLastWeight = index == weights.size() - 1;
            BigDecimal share = targetAmt.subtract(allocatedAmt);
            if (!isLastWeight) {
                share = proportionalShare(targetAmt, weights.get(index), totalWeight);
            }
            final BigDecimal normalizedShare = normalize(share);
            allocations.add(normalizedShare);
            allocatedAmt = allocatedAmt.add(normalizedShare);
        }
        return allocations;
    }

    private static BigDecimal proportionalShare(final BigDecimal targetAmt,
                                                final BigDecimal weight,
                                                final BigDecimal totalWeight) {
        BigDecimal share = ZERO_AMT;
        final BigDecimal normalizedWeight = normalize(weight);
        if (normalizedWeight.compareTo(ZERO_AMT) > 0) {
            share = targetAmt
                    .multiply(normalizedWeight)
                    .divide(totalWeight, CURRENCY_SCALE, RoundingMode.HALF_UP);
        }
        return share;
    }

    private static List<BigDecimal> zeroAllocation(final int size) {
        final List<BigDecimal> zeros = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            zeros.add(ZERO_AMT);
        }
        return zeros;
    }
}
