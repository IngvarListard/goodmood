CREATE TABLE user_ai_settings (
  user_id              INTEGER PRIMARY KEY REFERENCES users(id),
  master_enabled       INTEGER NOT NULL DEFAULT 1,
  correlations_enabled INTEGER NOT NULL DEFAULT 1,
  labels_enabled       INTEGER NOT NULL DEFAULT 1,
  advice_enabled       INTEGER NOT NULL DEFAULT 1
);