# HermesAI Architecture

## HLD

HermesAI is a distributed decision platform built as seven Java microservices behind Spring Cloud Gateway. External clients call REST APIs through the gateway. Internal services exchange domain events through Kafka. PostgreSQL stores authoritative state for decisions, reviews, workflows, feedback, and audits. Redis is used for idempotency keys and priority review queues.

## Core flow

1. Client sends `POST /api/v1/decisions`.
2. Decision service evaluates policy plus mock ML score.
3. Workflow orchestrator persists workflow state.
4. If human review is needed, the orchestrator emits a review request.
5. Human review service creates a queued task and tracks SLA.
6. Reviewer approves, rejects, or overrides.
7. Feedback learning service captures the correction signal.
8. Audit service stores immutable event history.
9. Notification service sends reviewer notifications.

## Service responsibilities

- API Gateway: routing, edge exposure, ingress boundary
- Decision Service: scoring, policy thresholding, idempotent decision creation
- Human Review Service: pending review queue, reviewer actions, SLA and priority
- Workflow Orchestrator: authoritative state machine transitions
- Feedback Learning Service: correction dataset and model version simulation
- Notification Service: reviewer alerts over email/WebSocket-style channels
- Audit Logging Service: append-only traceability

## Storage model

- PostgreSQL: durable business state per service
- Redis: `decision:idempotency:*` keys and `reviews:priority` sorted set
- Kafka: domain event backbone

## Observability

- Prometheus metrics on `/actuator/prometheus`
- OpenTelemetry tracer hook in decision path
- Structured JSON logging with correlation IDs

