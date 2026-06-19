CREATE TABLE IF NOT EXISTS courses (
    id               UUID          NOT NULL,
    title            VARCHAR(500)  NOT NULL,
    description      TEXT,
    instructor_id    UUID          NOT NULL,
    instructor_name  VARCHAR(255)  NOT NULL,
    video_url        VARCHAR(1000),
    video_duration   INTEGER       NOT NULL DEFAULT 0,
    thumbnail_url    VARCHAR(1000),
    upload_status    VARCHAR(50)   NOT NULL DEFAULT 'NONE',
    ai_summary       TEXT,
    kb_ingested      BOOLEAN       NOT NULL DEFAULT FALSE,
    is_published     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_courses PRIMARY KEY (id),
    CONSTRAINT uq_instructor_title UNIQUE (instructor_id, title)
);
 
 