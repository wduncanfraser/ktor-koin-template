SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: util; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA util;


--
-- Name: SCHEMA util; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA util IS 'Schema to contain any utility functions common to the database.';


--
-- Name: uuid-ossp; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;


--
-- Name: EXTENSION "uuid-ossp"; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION "uuid-ossp" IS 'generate universally unique identifiers (UUIDs)';


--
-- Name: f_update_standard_modified_fields(); Type: FUNCTION; Schema: util; Owner: -
--

CREATE FUNCTION util.f_update_standard_modified_fields() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN

    NEW.modified_at = CURRENT_TIMESTAMP;

RETURN NEW;
END
$$;


--
-- Name: FUNCTION f_update_standard_modified_fields(); Type: COMMENT; Schema: util; Owner: -
--

COMMENT ON FUNCTION util.f_update_standard_modified_fields() IS 'This function is used by triggers to update the standard attributes in a table that need to be updated when a record changes.';


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: schema_migrations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.schema_migrations (
    version character varying NOT NULL
);


--
-- Name: todo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.todo (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name text NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    modified_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: TABLE todo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.todo IS 'Temporary table to verify integration test functionality. Delete before deploying to a real environment.';


--
-- Name: COLUMN todo.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.todo.id IS 'The unique id (Primary Key) for this table.';


--
-- Name: COLUMN todo.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.todo.name IS 'The name for the todo record.';


--
-- Name: COLUMN todo.completed_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.todo.completed_at IS 'The time the todo was marked as completed.';


--
-- Name: COLUMN todo.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.todo.created_at IS 'The date / time that this record was created.';


--
-- Name: COLUMN todo.modified_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.todo.modified_at IS 'The date / time that this record was last modified.';


--
-- Name: schema_migrations schema_migrations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schema_migrations
    ADD CONSTRAINT schema_migrations_pkey PRIMARY KEY (version);


--
-- Name: todo todo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.todo
    ADD CONSTRAINT todo_pkey PRIMARY KEY (id);


--
-- Name: todo trg_table_modified; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_table_modified BEFORE UPDATE ON public.todo FOR EACH ROW EXECUTE FUNCTION util.f_update_standard_modified_fields();


--
-- PostgreSQL database dump complete
--


--
-- Dbmate schema migrations
--

INSERT INTO public.schema_migrations (version) VALUES
    ('20250719163205'),
    ('20250719163501'),
    ('20250719174315');
