ALTER TABLE users DROP COLUMN IF EXISTS user_token;

CREATE TABLE user_tokens (
    id bigserial PRIMARY KEY,
    user_id int8 NOT NULL,
    token text NOT NULL,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_tokens_token ON user_tokens(token);
CREATE INDEX idx_user_tokens_user_id ON user_tokens(user_id);
