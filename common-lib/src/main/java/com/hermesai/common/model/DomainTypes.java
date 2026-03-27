package com.hermesai.common.model;

public final class DomainTypes {

    private DomainTypes() {
    }

    public enum DecisionOutcome {
        APPROVE,
        REJECT,
        ESCALATE
    }

    public enum WorkflowState {
        CREATED,
        AI_PROCESSED,
        HUMAN_PENDING,
        APPROVED,
        REJECTED,
        COMPLETED
    }

    public enum ReviewAction {
        APPROVE,
        REJECT,
        OVERRIDE
    }

    public enum PriorityLevel {
        LOW,
        MEDIUM,
        HIGH,
        URGENT
    }
}
