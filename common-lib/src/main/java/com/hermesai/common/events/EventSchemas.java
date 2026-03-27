package com.hermesai.common.events;

import com.hermesai.common.model.DomainTypes;
import java.time.Instant;

public final class EventSchemas {

    private EventSchemas() {
    }

    public static class DecisionEvaluatedEvent {
        public String decisionId;
        public String externalReference;
        public String aiDecision;
        public double confidenceScore;
        public boolean requiresHumanReview;
        public String riskCategory;
        public Instant occurredAt;
        public String payloadSnapshot;
    }

    public static class ReviewRequestedEvent {
        public String decisionId;
        public String reviewId;
        public String assignedReviewer;
        public String priority;
        public Instant dueAt;
        public Instant occurredAt;
    }

    public static class ReviewCompletedEvent {
        public String decisionId;
        public String reviewId;
        public String reviewerId;
        public String finalDecision;
        public String action;
        public String comments;
        public boolean correctedAiDecision;
        public Instant occurredAt;
    }

    public static class WorkflowStateChangedEvent {
        public String workflowId;
        public String decisionId;
        public DomainTypes.WorkflowState state;
        public String actor;
        public Instant occurredAt;
    }

    public static class FeedbackCapturedEvent {
        public String decisionId;
        public String reviewId;
        public String modelVersion;
        public boolean corrected;
        public Instant occurredAt;
    }

    public static class NotificationEvent {
        public String eventId;
        public String decisionId;
        public String recipient;
        public String channel;
        public String message;
        public Instant occurredAt;
    }

    public static class AuditEvent {
        public String auditId;
        public String decisionId;
        public String actor;
        public String eventType;
        public String payload;
        public Instant occurredAt;
    }
}
