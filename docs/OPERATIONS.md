# Operations, Failure Modes, and Trade-offs

## Failure scenarios

### AI failure

- Decision service should fail fast and return 5xx if scoring dependencies are unavailable.
- Because decisions are idempotent, safe retries can reuse the same `Idempotency-Key`.

### Kafka downtime

- REST writes can still persist local state, but downstream orchestration lags.
- Production hardening would add an outbox table plus background publisher for guaranteed delivery.

### Human delay

- Review tasks carry due times and priority.
- Redis sorted set supports urgent-first draining and SLA breach monitoring.

## Trade-offs

- Local setup shares one PostgreSQL instance for speed, while production would isolate service databases.
- Mock ML scoring keeps the architecture pluggable without forcing heavyweight model hosting.
- Notification service currently keeps delivery history in memory; production would persist delivery attempts and retries.

## Scaling strategy

- Scale stateless services horizontally behind the gateway.
- Use Kafka consumer groups for parallel event handling.
- Partition high-volume topics by `decisionId`.
- Move reviewer assignment to a dedicated allocator if queue depth grows.
- Add PostgreSQL read replicas for audit-heavy reads.

