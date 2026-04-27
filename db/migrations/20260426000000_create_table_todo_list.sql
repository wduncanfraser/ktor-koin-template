-- migrate:up
CREATE TABLE public.todo_list (
    id UUID PRIMARY KEY DEFAULT (GEN_RANDOM_UUID()),
    name TEXT NOT NULL,
    description TEXT NULL,
    created_by_user_id TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER trg_todo_list_modified BEFORE UPDATE ON public.todo_list
    FOR EACH ROW EXECUTE PROCEDURE util.f_update_standard_modified_fields();

CREATE INDEX idx_todo_list_created_by_user_id ON public.todo_list (created_by_user_id);

COMMENT ON TABLE public.todo_list IS 'A named collection of todo items belonging to a user.';
COMMENT ON COLUMN public.todo_list.id IS 'The unique id (Primary Key) for this table.';
COMMENT ON COLUMN public.todo_list.name IS 'The name of the todo list.';
COMMENT ON COLUMN public.todo_list.description IS 'An optional description of the todo list.';
COMMENT ON COLUMN public.todo_list.created_by_user_id IS 'The id of the user who created this todo list.';
COMMENT ON COLUMN public.todo_list.created_at IS 'The date / time that this record was created.';
COMMENT ON COLUMN public.todo_list.updated_at IS 'The date / time that this record was last updated.';

-- migrate:down
DROP TABLE public.todo_list;
