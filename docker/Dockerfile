# StokVault container image (SDD 9.4): Payara Server + the StokVault WAR.
#
#   docker compose up --build      (from the project folder; see docker-compose.yml)
#
# Stage 1 builds and tests the WAR with Maven; stage 2 is the official Payara image with the
# PostgreSQL driver, a start-up script that creates the jdbc/StokVaultDS connection pool from
# environment variables, and the WAR in the auto-deploy folder.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q package

FROM payara/server-full:7.2026.9

# Database connection (DB_PASSWORD has no default: pass it in, e.g. from docker-compose's .env)
ENV DB_HOST=db \
    DB_PORT=5432 \
    DB_NAME=stokvault \
    DB_USER=stokvault

# JDBC driver for Payara's connection pool
COPY --from=build --chown=payara:payara \
     /root/.m2/repository/org/postgresql/postgresql/42.7.7/postgresql-42.7.7.jar \
     /opt/payara/appserver/glassfish/domains/domain1/lib/

# Runs before the image's own init scripts, so the pool exists before the WAR deploys
COPY --chown=payara:payara docker/init_0_stokvault_datasource.sh /opt/payara/scripts/

# Anything in the deployments folder is deployed when the container starts
COPY --from=build --chown=payara:payara /src/target/stokvault.war /opt/payara/deployments/

EXPOSE 8080 4848
