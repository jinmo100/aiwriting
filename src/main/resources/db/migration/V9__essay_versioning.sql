-- Essay revision chain support.

ALTER TABLE essays ADD COLUMN IF NOT EXISTS essay_group_id BIGINT;
ALTER TABLE essays ADD COLUMN IF NOT EXISTS version_no INTEGER NOT NULL DEFAULT 1;
ALTER TABLE essays ADD COLUMN IF NOT EXISTS parent_essay_id BIGINT;

UPDATE essays
SET essay_group_id = id
WHERE essay_group_id IS NULL;

ALTER TABLE essays DROP CONSTRAINT IF EXISTS fk_essays_group;
ALTER TABLE essays ADD CONSTRAINT fk_essays_group
    FOREIGN KEY (essay_group_id) REFERENCES essays(id) ON DELETE CASCADE;

ALTER TABLE essays DROP CONSTRAINT IF EXISTS fk_essays_parent;
ALTER TABLE essays ADD CONSTRAINT fk_essays_parent
    FOREIGN KEY (parent_essay_id) REFERENCES essays(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_essays_user_group_version
    ON essays(user_id, essay_group_id, version_no);

CREATE INDEX IF NOT EXISTS idx_essays_user_group_created_at
    ON essays(user_id, essay_group_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_essays_parent
    ON essays(parent_essay_id);
