#!/bin/bash
# Container start-up: create the jdbc/StokVaultDS connection pool from environment variables.
# The Payara image runs every /opt/payara/scripts/init_*.sh before starting the server, and runs
# the asadmin commands in $POSTBOOT_COMMANDS once the server is up (before deploying the WAR).
set -e

if [ -z "${DB_PASSWORD}" ]; then
    echo "StokVault: DB_PASSWORD is not set" >&2
    exit 1
fi

# In asadmin --property values ':' separates settings, so escape any ':' in the password
ESCAPED_PASSWORD="${DB_PASSWORD//:/\\:}"

cat >> "${POSTBOOT_COMMANDS}" <<EOF
create-jdbc-connection-pool --datasourceclassname org.postgresql.ds.PGSimpleDataSource --restype javax.sql.DataSource --property user=${DB_USER}:password=${ESCAPED_PASSWORD}:serverName=${DB_HOST}:portNumber=${DB_PORT}:databaseName=${DB_NAME}:stringtype=unspecified StokVaultPool
create-jdbc-resource --connectionpoolid StokVaultPool jdbc/StokVaultDS
EOF
