CREATE TABLE IF NOT EXISTS workflow_instances (
    id BIGSERIAL PRIMARY KEY,
    workflow_id VARCHAR(100) NOT NULL UNIQUE,
    decision_id VARCHAR(100) NOT NULL UNIQUE,
    state VARCHAR(50) NOT NULL,
    last_updated_by VARCHAR(100),
    updated_at TIMESTAMP NOT NULL
);
