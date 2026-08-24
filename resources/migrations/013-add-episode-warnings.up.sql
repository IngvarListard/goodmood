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
