## Context

`ServiceCatalogService` currently has `deleteService(userId, serviceId)` for single deletion. It blocks deletion when a service is linked to calendar events via:
- `CalendarEventServiceLinkRepository.existsByServiceId(serviceId)`
- `CalendarEventRepository.existsByServiceId(serviceId)`

The new endpoint must apply the same link safety rule, but across a list of IDs and without failing the whole request when one ID is blocked.

## Goals / Non-Goals

**Goals:**
- Add `POST /api/services/delete` with body `[id1, id2, idN]`
- Process each ID independently under the authenticated user scope
- Skip linked services and count them in `hasLink`
- Delete unlinked services and count them in `deleted`
- Return deterministic summary payload

**Non-Goals:**
- Replacing existing `DELETE /api/services/{id}`
- Returning per-ID detailed status in response
- Cross-user deletions or admin-level operations
- Transactional all-or-nothing batch semantics

## Decisions

### 1. Endpoint contract

**Decision**: Add `POST /api/services/delete`.

- Request body: JSON array of `Long`
- Response body:
  - `deleted` (integer)
  - `hasLink` (integer)
- Response status: `200 OK`

**Rationale**: Keeps payload simple and matches requested contract.

### 2. Ownership and visibility

**Decision**: Only services owned by the authenticated user are considered.

- IDs not found for that user are ignored
- IDs from another user are ignored (same behavior as not found)
- Ignored IDs do not increment `deleted` or `hasLink`

**Rationale**: Maintains user isolation and avoids leaking resource existence.

### 3. Processing algorithm

**Decision**: Implement in service layer with per-service link checks.

Flow:
1. Receive list of IDs
2. Remove duplicates and nulls
3. Load services by `userId` + `id IN (...)`
4. For each loaded service:
   - if linked (`existsByServiceId` in link/event repositories): increment `hasLink`
   - else delete and increment `deleted`
5. Return summary DTO

**Rationale**: Reuses current integrity rules and keeps behavior explicit.

### 4. Validation behavior

**Decision**:
- Empty body list is valid and returns `{"deleted":0,"hasLink":0}`
- Non-array or invalid JSON remains `400 Bad Request` via Spring parsing
- Null IDs in array are ignored during processing

**Rationale**: Bulk operations should be resilient and idempotent for client retries.

## Risks / Trade-offs

**[N+1 link checks]**: Each candidate service performs link existence checks. For typical small lists this is acceptable. If batch sizes grow significantly, we can optimize later with bulk link lookups.

**[Silent ignore of unknown IDs]**: Clients do not get per-ID feedback, which reduces debuggability but avoids information leakage and keeps response shape minimal.
