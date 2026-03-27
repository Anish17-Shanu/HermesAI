CREATE TABLE IF NOT EXISTS feedback_records (
    id BIGSERIAL PRIMARY KEY,
    decision_id VARCHAR(100) NOT NULL,
    review_id VARCHAR(100) NOT NULL UNIQUE,
    model_version VARCHAR(100) NOT NULL,
    corrected BOOLEAN NOT NULL,
    improvement_signal VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL
);
