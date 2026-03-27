package com.hermesai.common.config;

public final class KafkaTopics {

    public static final String DECISION_EVALUATED = "hermes.decision.evaluated";
    public static final String REVIEW_REQUESTED = "hermes.review.requested";
    public static final String REVIEW_COMPLETED = "hermes.review.completed";
    public static final String WORKFLOW_CHANGED = "hermes.workflow.changed";
    public static final String FEEDBACK_CAPTURED = "hermes.feedback.captured";
    public static final String NOTIFICATIONS = "hermes.notifications";
    public static final String AUDIT_EVENTS = "hermes.audit.events";

    private KafkaTopics() {
    }
}
