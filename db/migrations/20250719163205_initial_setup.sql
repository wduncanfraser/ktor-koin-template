-- Migration for initial DB setup. Creates util for common DB functions, and registers necessary extensions

-- migrate:up
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE SCHEMA IF NOT EXISTS util;
COMMENT ON SCHEMA util IS 'Schema to contain any utility functions common to the database.';

CREATE OR REPLACE FUNCTION util.f_update_standard_modified_fields()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN

    NEW.modified_at = CURRENT_TIMESTAMP;

RETURN NEW;
END
$$;

COMMENT ON FUNCTION util.f_update_standard_modified_fields IS 'This function is used by triggers to update the standard attributes in a table that need to be updated when a record changes.'; -- noqa: LT01

-- migrate:down
DROP FUNCTION IF EXISTS util.f_update_standard_modified_fields();
DROP SCHEMA IF EXISTS util;
