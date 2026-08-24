CREATE TABLE ai_findings (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id     INTEGER NOT NULL REFERENCES users(id),
  type        TEXT NOT NULL CHECK (type IN ('correlation','label','advice')),
  content     TEXT NOT NULL,
  confidence  TEXT CHECK (confidence IN ('high','medium','low')),
  source_refs TEXT,
  feedback    TEXT,
  hidden      INTEGER NOT NULL DEFAULT 0,
  created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

--;;

CREATE INDEX idx_ai_findings_user_type
  ON ai_findings (user_id, type, hidden, created_at);