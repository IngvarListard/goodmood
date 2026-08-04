CREATE TABLE entries (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  date        TEXT NOT NULL,
  activity    TEXT NOT NULL,
  effect      TEXT NOT NULL,
  mood_score  INTEGER NOT NULL CHECK (mood_score BETWEEN 0 AND 10),
  sleep_hours REAL,
  created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);
