package com.hermesai.common.api;

import com.hermesai.common.model.DomainTypes;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class ApiContracts {

    private ApiContracts() {
    }

    public static class DecisionRequest {
        public String externalReference;
        public String riskCategory;
        public Double amount;
        public String payload;
        public Boolean synchronousReview;
        public Map<String, String> metadata = new HashMap<String, String>();
    }

    public static class DecisionResponse {
        public String decisionId;
        public DomainTypes.DecisionOutcome decision;
        public double confidenceScore;
        public boolean requiresHumanReview;
        public DomainTypes.WorkflowState workflowState;
        public String assignedReviewer;
        public Instant createdAt;
    }

    public static class ReviewActionRequest {
        public String reviewerId;
        public String overrideDecision;
        public String comments;
    }

    public static class WorkflowView {
        public String workflowId;
        public String decisionId;
        public DomainTypes.WorkflowState state;
        public String lastUpdatedBy;
        public Instant updatedAt;
    }

    public static class FeedbackSummary {
        public String decisionId;
        public String modelVersion;
        public boolean correctedByHuman;
        public String improvementSignal;
    }

    public static class NotificationView {
        public String channel;
        public String recipient;
        public String message;
        public Instant createdAt;
    }
}
