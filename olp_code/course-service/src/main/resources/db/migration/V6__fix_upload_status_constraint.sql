ALTER TABLE courses DROP CONSTRAINT IF EXISTS chk_upload_status;

ALTER TABLE courses ADD CONSTRAINT chk_upload_status
  CHECK (upload_status IN (
    'NONE','PENDING','PROCESSING','READY','FAILED',
    'none','pending','processing','ready','failed'
  ));