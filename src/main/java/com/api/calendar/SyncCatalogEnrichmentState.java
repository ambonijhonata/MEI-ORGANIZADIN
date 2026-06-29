package com.api.calendar;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class SyncCatalogEnrichmentState {
    private static final long FIRST_REVISION = 1L;
    private static final long NO_REVISION = 0L;

    @Column(name = "catalog_enrichment_revision_requested", nullable = false)
    private long requestedRevision;

    @Column(name = "catalog_enrichment_revision_applied", nullable = false)
    private long appliedRevision;

    protected SyncCatalogEnrichmentState() {
    }

    public long getRequestedRevision() {
        return requestedRevision;
    }

    public long getAppliedRevision() {
        return appliedRevision;
    }

    public long request() {
        requestedRevision = Math.max(requestedRevision + 1, appliedRevision + 1);
        return requestedRevision;
    }

    public boolean hasPending() {
        return appliedRevision < requestedRevision;
    }

    public void markApplied(final long revisionToApply) {
        if (revisionToApply <= NO_REVISION) {
            return;
        }
        appliedRevision = Math.max(
                appliedRevision,
                Math.min(revisionToApply, requestedRevision)
        );
    }

    public void ensureBackfillPending() {
        if (requestedRevision == NO_REVISION) {
            requestedRevision = Math.max(FIRST_REVISION, appliedRevision + 1);
        }
    }
}
