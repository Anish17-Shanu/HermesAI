CREATE TABLE IF NOT EXISTS review_tasks (
    id BIGSERIAL PRIMARY KEY,
    review_id VARCHAR(100) NOT NULL UNIQUE,
    decision_id VARCHAR(100) NOT NULL,
    assigned_reviewer VARCHAR(100),
    reviewer_id VARCHAR(100),
    priority VARCHAR(20),
    status VARCHAR(50) NOT NULL,
    final_decision VARCHAR(50),
    comments VARCHAR(2048),
    due_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);
