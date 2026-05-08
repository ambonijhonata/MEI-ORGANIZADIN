## 1. Enrichment Domain Rules

- [x] 1.1 Map the persisted event/service state needed to detect when a synchronized appointment can gain new service associations from its saved title.
- [x] 1.2 Implement a dedicated enrichment path that adds only missing matched services to an existing appointment without overwriting snapshots of already linked services.
- [x] 1.3 Update the event aggregate snapshot rules so enriched appointments recalculate their total service value while preserving canonical existing links.

## 2. Reprocessing Orchestration

- [x] 2.1 Add an asynchronous reprocessing method that re-evaluates synchronized appointments for late-created or late-renamed services under the existing per-user execution lock.
- [x] 2.2 Update `ServiceCatalogService` to trigger appointment enrichment on service creation and on description-changing updates, while skipping value-only updates.
- [x] 2.3 Ensure the regular synchronization flow treats enriched appointments as stable state and does not duplicate or discard equivalent associations on later syncs.

## 3. Regression Coverage

- [x] 3.1 Add unit tests for a partially identified appointment that later gains missing services after catalog creation while keeping the original snapshot intact.
- [x] 3.2 Add unit tests for an unidentified appointment becoming identified after catalog enrichment and for value-only service updates preserving historical snapshots.
- [x] 3.3 Add sync/regression tests covering the full sequence: initial partial match, later service creation, enrichment of the existing appointment, and stable follow-up synchronization.
- [x] 3.4 Add financial-report assertions confirming the enriched appointment total matches the final set of linked services without double counting.

## 4. Verification

- [x] 4.1 Run the focused backend test suite for calendar sync, reprocessing, and financial reports.
- [x] 4.2 Execute a manual or documented smoke scenario for `sobrancelha + buco + tintura` to confirm already synchronized appointments gain the late-created services.
