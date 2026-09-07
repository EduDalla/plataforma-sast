CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL
);
ALTER TABLE analyses ADD COLUMN user_id UUID REFERENCES app_users(id);
CREATE INDEX analyses_user_id_idx ON analyses(user_id);
