# StokVault

Jakarta EE 11 backend for managing stokvel contributions and payouts (DICT312 coursework).
It uses only standard Jakarta EE: JAX-RS, CDI, EJB and JPA. It runs on Payara Server 7 and stores data in PostgreSQL.

## Project layout

```
src/main/java/com/stokvault/
  config/ApplicationConfig.java     JAX-RS on, all endpoints under /api
  entity/Member.java                JPA entity -> "members" table
  service/MemberService.java        @Stateless EJB, transactions + database access
  resource/HealthCheckResource.java GET /api/health
  resource/MemberResource.java      GET/POST /api/members, GET /api/members/{id}
src/main/resources/META-INF/persistence.xml   JPA config, uses jdbc/StokVaultDS
src/main/webapp/WEB-INF/beans.xml             empty file that switches on CDI
```

Each new feature follows the same path: **Resource** (HTTP) → **Service** (EJB, transaction) → **Entity** (JPA table).

About the empty `beans.xml`: a file with no content is valid. It marks the WAR as a CDI archive with
`bean-discovery-mode="annotated"`. That lets CDI manage `@Inject` targets such as the `@Stateless` service.

---

## 1. Set up PostgreSQL

### Forgot the `postgres` password?

PostgreSQL has no way to show an existing password. You can only set a new one.

**If PostgreSQL is not installed (or was uninstalled)**, reinstall it and pick a new password:

1. If an old `C:\Program Files\PostgreSQL\18\data` folder is still there, rename it (for example to `data.old`).
   The installer then starts a fresh cluster with your new password instead of reusing the old one.
2. Download the Windows installer from <https://www.postgresql.org/download/windows/> (EDB, version 18).
3. Run it, keep port **5432**, and set a password you'll remember for the `postgres` superuser.
   Leave pgAdmin 4 selected in the component list. It's the PostgreSQL equivalent of MySQL Workbench.

**If PostgreSQL is installed and running**, reset the password:

1. As Administrator, open `C:\Program Files\PostgreSQL\18\data\pg_hba.conf`.
   On the `host all all 127.0.0.1/32` and `::1/128` lines, change `scram-sha-256` to `trust`.
2. Restart the service with `Restart-Service postgresql-x64-18` (as Administrator), or restart it from services.msc.
3. Run `"C:\Program Files\PostgreSQL\18\bin\psql" -U postgres -h localhost`. It won't ask for a password.
   Then run `ALTER USER postgres WITH PASSWORD 'new-password';`
4. **Change `trust` back to `scram-sha-256`** and restart the service again.

### Create the database and an app user

Payara does **not** create the database for you. Open pgAdmin's Query Tool, or `psql -U postgres -h localhost`, and run:

```sql
CREATE USER stokvault WITH PASSWORD 'choose-a-password';
CREATE DATABASE stokvault OWNER stokvault;
```

A dedicated `stokvault` user means the app doesn't connect as the superuser.
You don't have to create the tables yourself: JPA creates the `members` table on first deploy (see `persistence.xml`).

---

## 2. Configure Payara (one time)

Run these from `C:\Users\jaget\OneDrive\Documents\payara7\bin`. On Windows the command is `asadmin.bat`.

Payara 7 needs **JDK 21 or newer**. On JDK 17 you get `UnsupportedClassVersionError ... class file version 65.0`.
The last line of `payara7\glassfish\config\asenv.bat` pins Payara to JDK 21, whatever `java` is on your PATH:

```
set AS_JAVA=C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot
```

The app itself still compiles for Java 17, and a Java 17 app runs fine on JDK 21.

```bash
# Start the server (admin console: http://localhost:4848)
asadmin start-domain domain1

# Give Payara the PostgreSQL JDBC driver. Maven has already downloaded it here:
#   %USERPROFILE%\.m2\repository\org\postgresql\postgresql\42.7.7\postgresql-42.7.7.jar
asadmin add-library "%USERPROFILE%\.m2\repository\org\postgresql\postgresql\42.7.7\postgresql-42.7.7.jar"
asadmin restart-domain domain1

# Create the connection pool (driver class: org.postgresql.ds.PGSimpleDataSource)
asadmin create-jdbc-connection-pool ^
  --datasourceclassname org.postgresql.ds.PGSimpleDataSource ^
  --restype javax.sql.DataSource ^
  --property "user=stokvault:password=choose-a-password:serverName=localhost:portNumber=5432:databaseName=stokvault" ^
  StokVaultPool

# Publish the pool under the JNDI name persistence.xml looks for
asadmin create-jdbc-resource --connectionpoolid StokVaultPool jdbc/StokVaultDS

# Check that Payara can reach the database. Expect "Command ping-connection-pool executed successfully."
asadmin ping-connection-pool StokVaultPool
```

(`^` continues a line in Windows cmd. In PowerShell, use a backtick `` ` `` instead, or put everything on one line.)

Notes:
- In `--property`, `:` separates the settings. If your password contains a `:`, escape it as `\:`.
- The driver class name for the **pool** is `org.postgresql.ds.PGSimpleDataSource`.
  If a tool asks for a plain `java.sql.Driver` class, that one is `org.postgresql.Driver`.
- If you mistype something, run `asadmin delete-jdbc-resource jdbc/StokVaultDS` and
  `asadmin delete-jdbc-connection-pool StokVaultPool`, then create them again.

---

## 3. Build

```bash
mvn clean package
```

This produces `target/stokvault.war`. In IntelliJ, you can also open the **Maven** tool window and run **Lifecycle → clean**, then **package**.

## 4. Deploy

Copy the WAR into Payara's autodeploy folder:

```
copy target\stokvault.war "C:\Users\jaget\OneDrive\Documents\payara7\glassfish\domains\domain1\autodeploy"
```

Within a few seconds a `stokvault.war_deployed` marker file appears next to it. If deployment fails, you get
`stokvault.war_deployFailed` instead. Check `glassfish\domains\domain1\logs\server.log` to see why.

## 5. Test

```bash
curl http://localhost:8081/stokvault/api/health
# {"status":"ok"}

curl -X POST http://localhost:8081/stokvault/api/members -H "Content-Type: application/json" -d "{\"name\":\"Thandi Mokoena\",\"email\":\"thandi@example.com\"}"
# 201 Created, returns the member with its new id

curl http://localhost:8081/stokvault/api/members
# [{"email":"thandi@example.com","id":1,"name":"Thandi Mokoena"}]
```

A POST with a blank name or an invalid email returns **400 Bad Request** (Bean Validation).
A POST that reuses an email that's already saved is rejected by the database's UNIQUE constraint.
