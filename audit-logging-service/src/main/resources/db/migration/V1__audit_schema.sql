CREATE TABLE IF NOT EXISTS audit_entries (
    id BIGSERIAL PRIMARY KEY,
    audit_id VARCHAR(100) UNIQUE,
    decision_id VARCHAR(100) NOT NULL,
    actor VARCHAR(100),
    event_type VARCHAR(100) NOT NULL,
    payload VARCHAR(2048),
    occurred_at TIMESTAMP NOT NULL
);
