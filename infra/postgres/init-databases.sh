#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE iamsso_auth;
    CREATE DATABASE iamsso_users;
    CREATE DATABASE iamsso_policies;
    CREATE DATABASE iamsso_notifications;
EOSQL
