CREATE TABLE insights (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id        INTEGER NOT NULL REFERENCES users(id),
  context        TEXT NOT NULL,
  category       TEXT NOT NULL CHECK (category IN ('productivity','coping','identity','general')),
  advice_to_self TEXT NOT NULL,
  identity       TEXT,
  state_label    TEXT,
  entry_id       INTEGER,
  created_at     TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at     TEXT NOT NULL DEFAULT (datetime('now'))
);

--;;

CREATE INDEX idx_insights_user_state
  ON insights (user_id, state_label);
