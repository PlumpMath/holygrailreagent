-- migration to be applied

CREATE TABLE IF NOT EXISTS logs2 (
  log_id bigserial primary key,
  instant timestamp NOT NULL,
  level varchar(20) NOT NULL,
  namespace varchar(50) NOT NULL,
  hostname varchar(30) NOT NULL,
  username varchar(30) NOT NULL,
  content text NOT NULL,
  error text NOT NULL
);
