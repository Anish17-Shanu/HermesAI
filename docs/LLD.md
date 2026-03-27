# Low-Level Design

## Decision service

- `DecisionController` exposes create and lookup APIs.
- `DecisionEngineService` performs scoring, policy checks, idempotency, and emits `DecisionEvaluatedEvent`.
- `DecisionRecordRepository` persists evaluated decisions.

## Human review service

- `HumanReviewApplicationService` listens to `ReviewRequestedEvent`.
- Review tasks are persisted and mirrored into a Redis sorted set for urgent prioritization.
- Reviewer actions emit `ReviewCompletedEvent` and `AuditEvent`.

## Workflow orchestrator

- Persists one workflow row per decision.
- Handles `DecisionEvaluatedEvent` and `ReviewCompletedEvent`.
- Emits workflow state changes and review requests.

## Feedback learning service

- Consumes `ReviewCompletedEvent`.
- Creates durable feedback rows and increments a simulated model version every ten review outcomes.

## Notification service

- Consumes review requests.
- Produces reviewer-facing notification records and emits `NotificationEvent`.

## Audit service

- Consumes `AuditEvent`.
- Stores ordered append-only entries keyed by decision id.

