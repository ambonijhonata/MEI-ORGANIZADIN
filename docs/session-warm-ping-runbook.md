# Session Warm Ping Runbook

## Purpose

Reduce Render cold-start impact on auth refresh by periodically warming `GET /healthz`.

## Ownership

- Primary owner: Backend/API maintainers
- Secondary owner: Mobile/API integration maintainers

## Warm Ping Automation

- Workflow: `.github/workflows/warm-render-health.yml`
- Cadence: every 5 minutes
- Target: `https://mei-organizadin.onrender.com/healthz`
- Request policy:
  - timeout: 30s
  - retries: 2
  - retry delay: 5s

## Failure Alerting

- Monitor failed workflow runs in GitHub Actions.
- Escalate if there are 3 consecutive failures or failure rate > 20% in 1 hour.
- If failures coincide with auth incidents, prioritize API startup/capacity investigation.

## Rollback Procedure

1. Disable schedule in GitHub Actions workflow if pinging behavior is causing platform issues.
2. Keep `/healthz` endpoint active (safe, low-cost) unless security posture changes demand closure.
3. Document rollback timestamp and reason in incident notes.

## Metrics To Monitor

Track these indicators before and after warm ping rollout:

- Refresh success rate (`auth_refresh_result status=SUCCESS|RETRY_SAFE_SUCCESS`)
- Refresh retryable failure rate (`code=REFRESH_RETRYABLE` in API error responses)
- Terminal refresh failure rate (`REFRESH_TOKEN_INVALID|REVOKED|REUSED|EXPIRED`)
- Forced logout/session clear events on Android
- API startup/cold-start frequency and startup duration

## Validation Checklist

1. Confirm `GET /healthz` returns HTTP 200 with JSON `{ "status": "ok", ... }`.
2. Confirm workflow executes on schedule.
3. Confirm failed pings are visible in Actions run history.
4. Compare auth refresh incident frequency before and after deployment.
