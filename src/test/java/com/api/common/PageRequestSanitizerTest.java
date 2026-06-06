package com.api.common;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PageRequestSanitizerTest {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "name", "createdAt");

    @Test
    void sanitizeOneBasedShouldConvertToZeroBasedPageRequest() {
        final PageRequest pageRequest = PageRequestSanitizer.sanitizeOneBased(
                2,
                25,
                "name",
                "desc",
                ALLOWED_SORT_FIELDS,
                100
        );

        assertEquals(1, pageRequest.getPageNumber());
        assertEquals(25, pageRequest.getPageSize());
        assertEquals(Sort.Direction.DESC, pageRequest.getSort().getOrderFor("name").getDirection());
    }

    @Test
    void sanitizeZeroBasedShouldRejectInvalidPage() {
        final InvalidRequestParameterException exception = assertThrows(
                InvalidRequestParameterException.class,
                () -> PageRequestSanitizer.sanitizeZeroBased(
                        -1,
                        10,
                        "name",
                        "asc",
                        ALLOWED_SORT_FIELDS,
                        100
                )
        );

        assertEquals("page", exception.getField());
        assertTrue(exception.getMessage().contains("greater than or equal to 0"));
    }

    @Test
    void sanitizePageableShouldUseDefaultsWhenPageableIsNull() {
        final Pageable sanitized = PageRequestSanitizer.sanitizePageable(
                null,
                ALLOWED_SORT_FIELDS,
                3,
                20,
                100
        );

        assertEquals(3, sanitized.getPageNumber());
        assertEquals(20, sanitized.getPageSize());
        assertTrue(sanitized.getSort().isUnsorted());
    }

    @Test
    void sanitizeSortShouldRejectUnknownField() {
        final InvalidRequestParameterException exception = assertThrows(
                InvalidRequestParameterException.class,
                () -> PageRequestSanitizer.sanitizeSort(
                        new PageRequestSanitizer.SortRequest("status", "asc", "sortBy", "direction"),
                        ALLOWED_SORT_FIELDS
                )
        );

        assertEquals("sortBy", exception.getField());
        assertTrue(exception.getMessage().contains("must be one of"));
    }

    @Test
    void sanitizePageableShouldRejectUnknownSortProperty() {
        final Pageable pageable = PageRequest.of(0, 10, Sort.by("status"));

        final InvalidRequestParameterException exception = assertThrows(
                InvalidRequestParameterException.class,
                () -> PageRequestSanitizer.sanitizePageable(
                        pageable,
                        ALLOWED_SORT_FIELDS,
                        0,
                        10,
                        100
                )
        );

        assertEquals("sort", exception.getField());
        assertTrue(exception.getMessage().contains("sort must use only"));
    }
}
