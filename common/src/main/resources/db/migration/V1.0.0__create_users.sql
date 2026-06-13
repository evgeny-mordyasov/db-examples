CREATE TABLE users (
    user_id    SERIAL PRIMARY KEY,
    username   VARCHAR(50) NOT NULL,
    email      VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
