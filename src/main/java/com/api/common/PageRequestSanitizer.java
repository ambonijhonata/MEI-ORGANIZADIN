package com.api.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.Set;
@SuppressWarnings({"PMD.AvoidLiteralsInIfCondition", "PMD.LawOfDemeter", "PMD.LongVariable", "PMD.OnlyOneReturn", "PMD.PrematureDeclaration", "PMD.UseExplicitTypes", "PMD.UseObjectForClearerAPI"})

public final class PageRequestSanitizer {

    private PageRequestSanitizer() {
    }

    public static PageRequest sanitizeOneBased(
            final int pageIndex,
            final int pageSize,
            final String sortBy,
            final String direction,
            final Set<String> allowedSortFields,
            final int maxPageSize
    ) {
        if (pageIndex < 1) {
            throw new InvalidRequestParameterException("pageIndex", "pageIndex must be greater than or equal to 1");
        }
        final int sanitizedSize = sanitizePageSize(pageSize, "itemsPerPage", maxPageSize);
        final Sort sort = sanitizeSort(sortBy, direction, allowedSortFields, "sortBy", "direction");
        return PageRequest.of(pageIndex - 1, sanitizedSize, sort);
    }

    public static PageRequest sanitizeZeroBased(
            final int page,
            final int size,
            final String sortBy,
            final String direction,
            final Set<String> allowedSortFields,
            final int maxPageSize
    ) {
        if (page < 0) {
            throw new InvalidRequestParameterException("page", "page must be greater than or equal to 0");
        }
        final int sanitizedSize = sanitizePageSize(size, "size", maxPageSize);
        final Sort sort = sanitizeSort(sortBy, direction, allowedSortFields, "sortBy", "direction");
        return PageRequest.of(page, sanitizedSize, sort);
    }

    public static Pageable sanitizePageable(
            final Pageable pageable,
            final Set<String> allowedSortFields,
            final int defaultPage,
            final int defaultSize,
            final int maxPageSize
    ) {
        final int page = pageable != null ? pageable.getPageNumber() : defaultPage;
        final int size = pageable != null ? pageable.getPageSize() : defaultSize;

        if (page < 0) {
            throw new InvalidRequestParameterException("page", "page must be greater than or equal to 0");
        }

        final int sanitizedSize = sanitizePageSize(size, "size", maxPageSize);
        final Sort sanitizedSort = sanitizePageableSort(pageable != null ? pageable.getSort() : Sort.unsorted(), allowedSortFields);

        if (sanitizedSort.isSorted()) {
            return PageRequest.of(page, sanitizedSize, sanitizedSort);
        }
        return PageRequest.of(page, sanitizedSize);
    }

    public static Sort sanitizeSort(
            final String sortBy,
            final String direction,
            final Set<String> allowedSortFields,
            final String sortFieldName,
            final String directionFieldName
    ) {
        if (sortBy == null || sortBy.isBlank() || !allowedSortFields.contains(sortBy)) {
            throw new InvalidRequestParameterException(
                    sortFieldName,
                    sortFieldName + " must be one of: " + String.join(", ", allowedSortFields)
            );
        }
        return Sort.by(parseDirection(direction, directionFieldName), sortBy);
    }

    private static Sort sanitizePageableSort(final Sort sort, final Set<String> allowedSortFields) {
        if (sort == null || sort.isUnsorted()) {
            return Sort.unsorted();
        }

        final var sanitizedOrders = new ArrayList<Sort.Order>();
        for (final Sort.Order order : sort) {
            final String property = order.getProperty();
            if (property == null || property.isBlank() || !allowedSortFields.contains(property)) {
                throw new InvalidRequestParameterException(
                        "sort",
                        "sort must use only: " + String.join(", ", allowedSortFields)
                );
            }
            sanitizedOrders.add(order.isDescending() ? Sort.Order.desc(property) : Sort.Order.asc(property));
        }
        return Sort.by(sanitizedOrders);
    }

    private static Sort.Direction parseDirection(final String direction, final String directionFieldName) {
        if (direction == null) {
            throw new InvalidRequestParameterException(directionFieldName, directionFieldName + " must be 'asc' or 'desc'");
        }
        if ("asc".equalsIgnoreCase(direction)) {
            return Sort.Direction.ASC;
        }
        if ("desc".equalsIgnoreCase(direction)) {
            return Sort.Direction.DESC;
        }
        throw new InvalidRequestParameterException(directionFieldName, directionFieldName + " must be 'asc' or 'desc'");
    }

    private static int sanitizePageSize(final int pageSize, final String fieldName, final int maxPageSize) {
        if (pageSize < 1) {
            throw new InvalidRequestParameterException(fieldName, fieldName + " must be greater than or equal to 1");
        }
        if (pageSize > maxPageSize) {
            throw new InvalidRequestParameterException(fieldName, fieldName + " must be less than or equal to " + maxPageSize);
        }
        return pageSize;
    }
}
