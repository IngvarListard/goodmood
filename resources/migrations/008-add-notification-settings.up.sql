CREATE TABLE user_notification_settings (
  user_id           INTEGER NOT NULL REFERENCES users(id),
  slot              TEXT NOT NULL CHECK (slot IN ('morning','midday','evening')),
  enabled           INTEGER NOT NULL DEFAULT 1,
  time              TEXT NOT NULL DEFAULT '08:00',
  last_summary_date TEXT,
  last_slot_shown   TEXT,
  PRIMARY KEY (user_id, slot)
);