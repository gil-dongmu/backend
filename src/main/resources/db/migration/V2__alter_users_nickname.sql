ALTER TABLE users ALTER COLUMN nickname DROP NOT NULL;
ALTER TABLE users ADD CONSTRAINT uq_users_nickname UNIQUE (nickname);
