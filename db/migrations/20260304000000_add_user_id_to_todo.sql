-- migrate:up
ALTER TABLE public.todo
    ADD COLUMN user_id TEXT NOT NULL DEFAULT '';

ALTER TABLE public.todo
    ALTER COLUMN user_id DROP DEFAULT;

CREATE INDEX idx_todo_user_id ON public.todo (user_id);

COMMENT ON COLUMN public.todo.user_id IS 'The id of the user who owns this todo item.';

-- migrate:down
DROP INDEX IF EXISTS idx_todo_user_id;
ALTER TABLE public.todo DROP COLUMN user_id;
