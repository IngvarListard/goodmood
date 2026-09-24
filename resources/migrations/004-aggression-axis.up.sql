-- 004-aggression-axis: четвёртая ось состояния. Nullable INTEGER без CHECK —
-- SQLite не поддерживает ADD COLUMN с CHECK; диапазон 0–10 валидирует домен
-- (как energy/anxiety в 001-init). Существующие записи получают NULL.
ALTER TABLE entries ADD COLUMN aggression INTEGER;
