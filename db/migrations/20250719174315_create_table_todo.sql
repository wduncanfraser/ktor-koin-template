-- migrate:up
CREATE TABLE IF NOT EXISTS public.todo (
    id UUID PRIMARY KEY DEFAULT (GEN_RANDOM_UUID()),

    name TEXT NOT NULL,
    completed_at TIMESTAMPTZ NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER trg_table_modified BEFORE UPDATE ON public.todo FOR EACH ROW EXECUTE PROCEDURE util.f_update_standard_modified_fields();

COMMENT ON TABLE public.todo IS 'Temporary table to verify integration test functionality. Delete before deploying to a real environment.';

COMMENT ON COLUMN public.todo.id IS 'The unique id (Primary Key) for this table.';
COMMENT ON COLUMN public.todo.name IS 'The name for the todo record.';
COMMENT ON COLUMN public.todo.completed_at IS 'The time the todo was marked as completed.';

COMMENT ON COLUMN public.todo.created_at IS 'The date / time that this record was created.';
COMMENT ON COLUMN public.todo.modified_at IS 'The date / time that this record was last modified.';

-- migrate:down
DROP TABLE IF EXISTS public.todo;
