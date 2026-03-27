# HermesAI

## Creator

This project was created, written, and maintained by **Anish Kumar**.

HermesAI is a production-style Java backend for human-in-the-loop autonomous decisions. It uses Maven, Spring Boot microservices, Kafka for internal events, PostgreSQL for durable state, Redis for fast queues and idempotency, Prometheus metrics, and JWT-based RBAC.

## Modules

- `common-lib`
- `api-gateway`
- `decision-service`
- `human-review-service`
- `workflow-orchestrator-service`
- `feedback-learning-service`
- `notification-service`
- `audit-logging-service`

## Build

```bash
mvn clean verify
```

## Run locally

```bash
docker-compose up --build
```

## Docs

- `docs/ARCHITECTURE.md`
- `docs/LLD.md`
- `docs/SEQUENCE_DIAGRAMS.md`
- `docs/OPERATIONS.md`
