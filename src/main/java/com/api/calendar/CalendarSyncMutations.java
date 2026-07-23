package com.api.calendar;

import java.util.List;
import java.util.Set;

public record CalendarSyncMutations(
        List<CalendarEventMutationPlan> upserts,
        List<CalendarEvent> deletions,
        Set<Long> replacementIds,
        int created,
        int updated,
        int deleted
) {
    public static CalendarSyncMutations empty() {
        return new CalendarSyncMutations(List.of(), List.of(), Set.of(), 0, 0, 0);
    }
}
