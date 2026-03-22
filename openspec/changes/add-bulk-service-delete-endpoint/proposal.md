## Why

The API currently supports deleting one service at a time with `DELETE /api/services/{id}`. This makes cleanup operations slow when users need to remove many services. It also blocks the operation when a service has linked calendar events, which is correct for data integrity, but not efficient for batch workflows.

We need a bulk-delete endpoint that processes each requested ID independently and returns a summary of what was deleted vs what was blocked by links.

## What Changes

- Add `POST /api/services/delete`
- Request body is a JSON array of service IDs:
  - `[id1, id2, idN]`
- For each requested ID that belongs to the authenticated user:
  - If service has at least one link with a Google event, skip deletion and increment `hasLink`
  - If service has no link, delete it and increment `deleted`
- Return HTTP 200 with summary payload:
  - `{"deleted": <amountOfDeletedServices>, "hasLink": <amountOfServicesWithLink>}`
- Keep existing single-delete endpoint unchanged

## Capabilities

### New Capabilities

### Modified Capabilities

- `service-catalog`: add batch service deletion with partial success behavior and summary response

## Impact

- **API**: new endpoint `POST /api/services/delete`
- **Service layer**: new bulk deletion flow in `ServiceCatalogService`
- **Repository layer**: query support to load owned services by ID list
- **Behavior**: linked services are ignored instead of failing the whole operation
- **Tests**: new unit/integration coverage for mixed deletion results
