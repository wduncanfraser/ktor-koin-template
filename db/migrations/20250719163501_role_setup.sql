-- Migration for application role setup.

-- migrate:up
COMMENT ON ROLE todo IS 'This is the application role that will be used by the service to write data to the database.';

GRANT pg_read_all_data TO todo;
GRANT pg_write_all_data TO todo;

-- migrate:down
REVOKE pg_read_all_data FROM todo;
REVOKE pg_write_all_data FROM todo;
