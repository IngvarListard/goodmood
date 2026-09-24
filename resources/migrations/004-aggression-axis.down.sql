-- DROP COLUMN поддерживается SQLite >= 3.35 (2021). Для более старых версий —
-- fallback через пересоздание таблицы (create new → copy → drop → rename).
ALTER TABLE entries DROP COLUMN aggression;
