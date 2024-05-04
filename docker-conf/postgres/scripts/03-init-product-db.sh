#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE USER $PRODUCT_DB_USER WITH PASSWORD '$PRODUCT_DB_PASSWORD';
    CREATE DATABASE $PRODUCT_DB_NAME;
    GRANT ALL PRIVILEGES ON DATABASE $PRODUCT_DB_NAME TO $PRODUCT_DB_USER;
    ALTER DATABASE $PRODUCT_DB_NAME SET TIMEZONE='Europe/Amsterdam';
    \c $PRODUCT_DB_NAME
    ALTER SCHEMA public OWNER TO $PRODUCT_DB_USER;
    GRANT ALL PRIVILEGES ON SCHEMA public TO $PRODUCT_DB_USER;
EOSQL
