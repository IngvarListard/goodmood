CREATE TABLE medications (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id    INTEGER NOT NULL REFERENCES users(id),
  name       TEXT NOT NULL,
  dose       REAL NOT NULL,
  dose_unit  TEXT NOT NULL,
  schedule   TEXT NOT NULL,
  active     INTEGER NOT NULL DEFAULT 1,
  sensitive  INTEGER NOT NULL DEFAULT 0,
  notes      TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

--;;

CREATE TABLE medication_logs (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id        INTEGER NOT NULL REFERENCES users(id),
  medication_id  INTEGER NOT NULL REFERENCES medications(id),
  log_date       TEXT NOT NULL,
  scheduled_time TEXT NOT NULL,
  status         TEXT NOT NULL CHECK (status IN ('taken','skipped','delayed')),
  taken_at       TEXT,
  actual_dose    REAL,
  notes          TEXT,
  created_at     TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE (medication_id, log_date, scheduled_time)
);

--;;

CREATE INDEX idx_medication_logs_user_date
  ON medication_logs (user_id, log_date);

--;;

CREATE TABLE medication_dose_changes (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  medication_id  INTEGER NOT NULL REFERENCES medications(id),
  user_id        INTEGER NOT NULL REFERENCES users(id),
  previous_dose  REAL NOT NULL,
  new_dose       REAL NOT NULL,
  changed_at     TEXT NOT NULL DEFAULT (datetime('now')),
  reason         TEXT
);