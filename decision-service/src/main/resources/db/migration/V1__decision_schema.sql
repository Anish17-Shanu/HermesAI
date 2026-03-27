CREATE TABLE IF NOT EXISTS decision_records (
    id BIGSERIAL PRIMARY KEY,
    decision_id VARCHAR(100) NOT NULL UNIQUE,
    external_reference VARCHAR(100) NOT NULL UNIQUE,
    risk_category VARCHAR(100),
    input_payload VARCHAR(2048),
    amount DOUBLE PRECISION NOT NULL,
    ai_decision VARCHAR(50) NOT NULL,
    confidence_score DOUBLE PRECISION NOT NULL,
    requires_human_review BOOLEAN NOT NULL,
    workflow_state VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
