-- Сессии чата (change add-chat-sessions, design D1):
-- сообщения группируются в разговоры; существующая лента = сессия 1.
ALTER TABLE ai_chat_messages ADD COLUMN session_id INTEGER NOT NULL DEFAULT 1;