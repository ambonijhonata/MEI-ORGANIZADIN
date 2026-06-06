package com.api.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PageRequestSanitizer {

    private static final String PAGE_INDEX_FIELD = "pageIndex";
    private static final String PAGE_FIELD = "page";
    private static final String PER_PAGE_FIELD = "itemsPerPage";
    private static final String SIZE_FIELD = "size";
    private static final String SORT_BY_FIELD = "sortBy";
    private static final String SORT_FIELD = "sort";
    private static final String DIR_FIELD = "direction";
    private static final String PAGE_INDEX_MSG = "pageIndex must be greater than or equal to 1";
    private static final String PAGE_MSG = "page must be greater than or equal to 0";
    private static final String MIN_MSG = " must be greater than or equal to 1";
    private static final String MAX_MSG = " must be less than or equal to ";
    private static final String SORT_VALS_MSG = " must be one of: ";
    private static final String SORT_USE_MSG = "sort must use only: ";
    private static final String DIR_MSG = " must be 'asc' or 'desc'";
    private static final int MIN_PAGE_INDEX = 1;
    private static final int MIN_PAGE_VALUE = 0;
    private static final int MIN_PAGE_SIZE = 1;

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
        if (pageIndex < MIN_PAGE_INDEX) {
            throw new InvalidRequestParameterException(PAGE_INDEX_FIELD, PAGE_INDEX_MSG);
        }
        final int sanitizedSize = sanitizePageSize(pageSize, PER_PAGE_FIELD, maxPageSize);
        final Sort sort = sanitizeSort(
                new SortRequest(sortBy, direction, SORT_BY_FIELD, DIR_FIELD),
                allowedSortFields
        );
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
            throw new InvalidRequestParameterException(PAGE_FIELD, PAGE_MSG);
        }
        final int sanitizedSize = sanitizePageSize(size, SIZE_FIELD, maxPageSize);
        final Sort sort = sanitizeSort(
                new SortRequest(sortBy, direction, SORT_BY_FIELD, DIR_FIELD),
                allowedSortFields
        );
        return PageRequest.of(page, sanitizedSize, sort);
    }

    public static Pageable sanitizePageable(
            final Pageable pageable,
            final Set<String> allowedSortFields,
            final int defaultPage,
            final int defaultSize,
            final int maxPageSize
    ) {
        final PageableInfo pageableInfo = resolvePageableInfo(pageable, defaultPage, defaultSize);

        if (pageableInfo.page() < MIN_PAGE_VALUE) {
            throw new InvalidRequestParameterException(PAGE_FIELD, PAGE_MSG);
        }

        final Sort sanitizedSort = sanitizePageableSort(pageableInfo.sort(), allowedSortFields);
        final int sanitizedSize = sanitizePageSize(pageableInfo.size(), SIZE_FIELD, maxPageSize);
        return sanitizedSort.isSorted()
                ? PageRequest.of(pageableInfo.page(), sanitizedSize, sanitizedSort)
                : PageRequest.of(pageableInfo.page(), sanitizedSize);
    }

    public static Sort sanitizeSort(
            final SortRequest request,
            final Set<String> allowedSortFields
    ) {
        if (request.sortBy() == null || request.sortBy().isBlank() || !allowedSortFields.contains(request.sortBy())) {
            throw new InvalidRequestParameterException(
                    request.sortFieldName(),
                    request.sortFieldName() + SORT_VALS_MSG + String.join(", ", allowedSortFields)
            );
        }
        return Sort.by(parseDirection(request.direction(), request.dirField()), request.sortBy());
    }

    private static Sort sanitizePageableSort(final Sort sort, final Set<String> allowedSortFields) {
        final Sort sanitizedSort;
        if (sort == null || sort.isUnsorted()) {
            sanitizedSort = Sort.unsorted();
        } else {
            final List<Sort.Order> orders = new ArrayList<>();
            for (final Sort.Order order : sort) {
                orders.add(sanitizeOrder(order, allowedSortFields));
            }
            sanitizedSort = orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
        }
        return sanitizedSort;
    }

    private static Sort.Order sanitizeOrder(final Sort.Order order, final Set<String> allowedSortFields) {
        final String property = order.getProperty();
        if (property == null || property.isBlank() || !allowedSortFields.contains(property)) {
            throw new InvalidRequestParameterException(
                    SORT_FIELD,
                    SORT_USE_MSG + String.join(", ", allowedSortFields)
            );
        }
        return order.isDescending() ? Sort.Order.desc(property) : Sort.Order.asc(property);
    }

    private static Sort.Direction parseDirection(final String direction, final String directionField) {
        if (direction == null) {
            throw new InvalidRequestParameterException(directionField, directionField + DIR_MSG);
        }
        final String normalizedDir = direction.toLowerCase(Locale.ROOT);
        return switch (normalizedDir) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new InvalidRequestParameterException(
                    directionField,
                    directionField + DIR_MSG
            );
        };
    }

    private static int sanitizePageSize(final int pageSize, final String fieldName, final int maxPageSize) {
        if (pageSize < MIN_PAGE_SIZE) {
            throw new InvalidRequestParameterException(fieldName, fieldName + MIN_MSG);
        }
        if (pageSize > maxPageSize) {
            throw new InvalidRequestParameterException(fieldName, fieldName + MAX_MSG + maxPageSize);
        }
        return pageSize;
    }

    private static PageableInfo resolvePageableInfo(final Pageable pageable, final int defaultPage, final int defaultSize) {
        return pageable == null
                ? new PageableInfo(defaultPage, defaultSize, Sort.unsorted())
                : new PageableInfo(pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort());
    }

    public record SortRequest(String sortBy, String direction, String sortFieldName, String dirField) {
    }

    private record PageableInfo(int page, int size, Sort sort) {
    }
}
