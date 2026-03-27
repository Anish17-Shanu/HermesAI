# Sequence Diagrams

## Decision with human review

```text
Client -> API Gateway -> Decision Service: POST /decisions
Decision Service -> PostgreSQL: persist AI decision
Decision Service -> Kafka: DecisionEvaluatedEvent
Workflow Service -> PostgreSQL: update HUMAN_PENDING
Workflow Service -> Kafka: ReviewRequestedEvent
Human Review Service -> PostgreSQL: create review task
Human Review Service -> Redis: enqueue by priority
Notification Service -> Reviewer: email/websocket notification
Reviewer -> API Gateway -> Human Review Service: approve/reject/override
Human Review Service -> Kafka: ReviewCompletedEvent
Workflow Service -> PostgreSQL: APPROVED/REJECTED -> COMPLETED
Feedback Service -> PostgreSQL: persist correction signal
Audit Service -> PostgreSQL: append audit trail
```

## Decision without human review

```text
Client -> API Gateway -> Decision Service
Decision Service -> Kafka: DecisionEvaluatedEvent(requiresHumanReview=false)
Workflow Service -> PostgreSQL: COMPLETED
Audit Service -> PostgreSQL: AI decision trace
```

