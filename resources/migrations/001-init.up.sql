-- Baseline-миграция: squash миграций 002-013 (git-история сохраняет исходники).
-- Эквивалентна финальной схеме после применения всех прежних миграций.
-- После первого деплоя файлы миграций append-only (см. AGENTS.md).

CREATE TABLE entries (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  date        TEXT NOT NULL,
  activity    TEXT NOT NULL,
  effect      TEXT NOT NULL,
  mood_score  INTEGER NOT NULL CHECK (mood_score BETWEEN 0 AND 10),
  sleep_hours REAL,
  created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);
--;;

CREATE TABLE users (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  email         TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  display_name  TEXT NOT NULL,
  role          TEXT NOT NULL DEFAULT 'user',
  created_at    TEXT NOT NULL DEFAULT (datetime('now'))
);

--;;

ALTER TABLE entries ADD COLUMN user_id INTEGER REFERENCES users(id);
--;;

ALTER TABLE entries ADD COLUMN energy INTEGER;

--;;

ALTER TABLE entries ADD COLUMN anxiety INTEGER;

--;;

ALTER TABLE entries ADD COLUMN focus INTEGER;

--;;

ALTER TABLE entries ADD COLUMN note TEXT;

--;;

ALTER TABLE entries ADD COLUMN template TEXT;
--;;

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
--;;

ALTER TABLE entries ADD COLUMN state_label TEXT;

--;;

ALTER TABLE entries ADD COLUMN state_period_id INTEGER;
--;;

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
--;;

CREATE TABLE user_notification_settings (
  user_id           INTEGER NOT NULL REFERENCES users(id),
  slot              TEXT NOT NULL CHECK (slot IN ('morning','midday','evening')),
  enabled           INTEGER NOT NULL DEFAULT 1,
  time              TEXT NOT NULL DEFAULT '08:00',
  last_summary_date TEXT,
  last_slot_shown   TEXT,
  PRIMARY KEY (user_id, slot)
);
--;;

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
--;;

CREATE TABLE user_ai_settings (
  user_id              INTEGER PRIMARY KEY REFERENCES users(id),
  master_enabled       INTEGER NOT NULL DEFAULT 1,
  correlations_enabled INTEGER NOT NULL DEFAULT 1,
  labels_enabled       INTEGER NOT NULL DEFAULT 1,
  advice_enabled       INTEGER NOT NULL DEFAULT 1
);
--;;

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
--;;

CREATE TABLE ai_chat_messages (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id     INTEGER NOT NULL REFERENCES users(id),
  role        TEXT NOT NULL CHECK (role IN ('user','assistant')),
  content     TEXT NOT NULL,
  created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

--;;

CREATE INDEX idx_ai_chat_messages_user ON ai_chat_messages (user_id, id);
--;;

-- Таблица предупреждений о возможном начале эпизода (Фаза 7).
-- Без FK (развязка), dismissed=0 — активные (показываются), dismissed=1 —
-- скрытые/«не показанные» (низкая уверенность логируется так же).
CREATE TABLE episode_warnings (
  id                  INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id             INTEGER NOT NULL REFERENCES users(id),
  type                TEXT NOT NULL CHECK (type IN ('depressive','hypomanic')),
  pattern_description TEXT NOT NULL,
  confidence          REAL NOT NULL,
  feedback            TEXT,
  dismissed           INTEGER NOT NULL DEFAULT 0,
  created_at          TEXT NOT NULL DEFAULT (datetime('now'))
);

--;;

CREATE INDEX idx_episode_warnings_user ON episode_warnings (user_id, id);

--;;

-- Opt-in колонка: предупреждения эпизодов выключены по умолчанию (DEFAULT 0).
ALTER TABLE user_ai_settings ADD COLUMN episode_warning_enabled INTEGER NOT NULL DEFAULT 0;
