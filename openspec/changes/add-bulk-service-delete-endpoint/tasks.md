## 1. Service Catalog Domain

- [x] 1.1 Add a summary DTO for bulk deletion result (`deleted`, `hasLink`)
- [x] 1.2 Add repository method to fetch owned services by ID list (`findByUserIdAndIdIn`)
- [x] 1.3 Implement `deleteServices(userId, ids)` in `ServiceCatalogService`
- [x] 1.4 Ensure implementation deduplicates IDs and ignores null/unknown/not-owned IDs
- [x] 1.5 Reuse existing link checks to block deletion when service has linked events

## 2. API Endpoint

- [x] 2.1 Add `POST /api/services/delete` in `ServiceCatalogController`
- [x] 2.2 Accept request body as array of IDs (`[id1, id2, idN]`)
- [x] 2.3 Return HTTP 200 with payload `{"deleted": X, "hasLink": Y}`
- [x] 2.4 Add Swagger/OpenAPI documentation for the new endpoint

## 3. Tests

- [x] 3.1 Add unit test: mixed list with linked and unlinked services returns correct counters
- [x] 3.2 Add unit test: all linked services returns `deleted=0` and `hasLink=n`
- [x] 3.3 Add unit test: unknown/not-owned IDs are ignored and not counted
- [x] 3.4 Add unit test: empty list returns zero counters
- [x] 3.5 Add integration test for `POST /api/services/delete` happy path and mixed-result path

## 4. Verification

- [ ] 4.1 Run test suite for service catalog package
- [ ] 4.2 Verify endpoint contract in Swagger UI
