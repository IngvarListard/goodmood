CREATE TABLE state_periods (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id     INTEGER NOT NULL REFERENCES users(id),
  label       TEXT NOT NULL,
  started_at  TEXT NOT NULL,
  ended_at    TEXT,
  notes       TEXT,
  created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

--;;

CREATE INDEX idx_state_periods_user ON state_periods (user_id, started_at);

--;;

ALTER TABLE user_ai_settings ADD COLUMN allow_novel_advice INTEGER NOT NULL DEFAULT 0;