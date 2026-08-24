DROP INDEX idx_episode_warnings_user;

--;;

DROP TABLE episode_warnings;

--;;

ALTER TABLE user_ai_settings DROP COLUMN episode_warning_enabled;
