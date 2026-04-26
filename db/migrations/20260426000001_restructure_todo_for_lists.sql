-- migrate:up
ALTER TABLE public.todo RENAME COLUMN user_id TO created_by_user_id;
ALTER INDEX idx_todo_user_id RENAME TO idx_todo_created_by_user_id;
ALTER TABLE public.todo ADD COLUMN todo_list_id UUID NOT NULL;
ALTER TABLE public.todo ADD CONSTRAINT fk_todo_list FOREIGN KEY (todo_list_id) REFERENCES public.todo_list(id) ON DELETE CASCADE;
CREATE INDEX idx_todo_todo_list_id ON public.todo (todo_list_id);

COMMENT ON COLUMN public.todo.created_by_user_id IS 'The id of the user who created this todo item.';
COMMENT ON COLUMN public.todo.todo_list_id IS 'The id of the todo list this item belongs to.';

-- migrate:down
DROP INDEX idx_todo_todo_list_id;
ALTER TABLE public.todo DROP CONSTRAINT fk_todo_list;
ALTER TABLE public.todo DROP COLUMN todo_list_id;
ALTER INDEX idx_todo_created_by_user_id RENAME TO idx_todo_user_id;
ALTER TABLE public.todo RENAME COLUMN created_by_user_id TO user_id;
