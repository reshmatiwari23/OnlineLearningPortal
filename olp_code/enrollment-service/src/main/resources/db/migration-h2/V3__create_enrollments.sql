CREATE TABLE IF NOT EXISTS enrollments (
    id           UUID      NOT NULL,
    course_id    UUID      NOT NULL,
    user_id      UUID      NOT NULL,
    enrolled_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    completed    BOOLEAN   NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMP,
    CONSTRAINT pk_enrollments PRIMARY KEY (id),
    CONSTRAINT uq_enrollment UNIQUE (course_id, user_id)
);
 
 