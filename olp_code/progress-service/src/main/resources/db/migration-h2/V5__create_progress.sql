CREATE TABLE IF NOT EXISTS video_progress (
    id                UUID      NOT NULL,
    user_id           UUID      NOT NULL,
    course_id         UUID      NOT NULL,
    current_time_secs INTEGER   NOT NULL DEFAULT 0,
    duration_secs     INTEGER   NOT NULL DEFAULT 0,
    percent_complete  INTEGER   NOT NULL DEFAULT 0,
    last_updated_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at      TIMESTAMP,
    CONSTRAINT pk_video_progress PRIMARY KEY (id),
    CONSTRAINT uq_progress UNIQUE (user_id, course_id)
);
 
 