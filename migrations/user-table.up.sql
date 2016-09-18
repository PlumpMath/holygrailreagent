-- migration to be applied

CREATE TABLE IF NOT EXISTS users (
  username varchar(30) primary key,
  password text NOT NULL,
);
