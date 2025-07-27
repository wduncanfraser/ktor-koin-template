#! /usr/bin/env bash

set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-SQL
  CREATE USER "todo" WITH PASSWORD 'todo';
  CREATE DATABASE "todo" WITH OWNER "todo";
SQL
