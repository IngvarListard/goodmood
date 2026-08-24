ALTER TABLE user_ai_settings DROP COLUMN allow_novel_advice;

--;;

DROP INDEX idx_state_periods_user;

--;;

DROP TABLE state_periods;