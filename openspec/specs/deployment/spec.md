# deployment Specification

## Purpose
TBD - created by archiving change add-synology-deploy. Update Purpose after archive.
## Requirements
### Requirement: Uberjar build via tools.build
The project SHALL provide an alias `:build` in deps.edn and a `build.clj` that produces a runnable uberjar with the main class `app.core` via `clj -T:build uberjar`.

#### Scenario: Uberjar is built
- **WHEN** `clj -T:build uberjar` is executed
- **THEN** a runnable uberjar is created in `target/`
- **AND** `java -jar <uberjar>` starts the application (server responds to GET / with 200)

### Requirement: Docker image
The project SHALL provide `deploy/Dockerfile` producing a self-contained image based on `eclipse-temurin:25-jre` that runs the uberjar, runs as a non-root user, limits JVM heap (`-Xmx384m`), and includes a HEALTHCHECK hitting the health-check endpoint.

#### Scenario: Image builds locally
- **WHEN** `docker build -f deploy/Dockerfile -t goodmood:<tag> .` is executed after an uberjar build
- **THEN** the image builds without errors

#### Scenario: Container starts and passes health-check
- **WHEN** the image is run with `GOODMOOD_SESSION_SECRET` set and a mounted data volume
- **THEN** the container starts the server on the configured port
- **AND** the HEALTHCHECK reports healthy after startup

#### Scenario: Container data lives outside the image
- **WHEN** the container writes to the SQLite database at `GOODMOOD_DB_PATH`
- **THEN** the database file resides on the mounted host volume, not inside the container filesystem

### Requirement: Deploy script with backup
The project SHALL provide `bin/deploy.sh` that performs the full deploy cycle against the Synology host over SSH: build uberjar locally, build docker image locally, stop the running container on the NAS, back up the SQLite database files to the backups directory on the NAS (keeping the 10 most recent backups), transfer the image via `docker save | ssh docker load`, start the container, and verify the health-check endpoint.

#### Scenario: Full deploy succeeds
- **WHEN** `bin/deploy.sh` is executed with the NAS reachable over SSH
- **THEN** a new image version is running on the NAS
- **AND** the health-check endpoint responds with HTTP 200

#### Scenario: Database backed up before deploy
- **WHEN** the deploy script stops the old container
- **THEN** the SQLite database files are copied into the backups directory on the NAS with a timestamped name before the new container starts

#### Scenario: Backup retention
- **WHEN** a deploy creates a new backup and more than 10 backups exist
- **THEN** the oldest backups are deleted so that exactly 10 remain

#### Scenario: Deploy fails visibly
- **WHEN** the health-check does not pass after starting the new container
- **THEN** the deploy script exits with a non-zero status and prints the failure

### Requirement: Rollback procedure
The system SHALL be recoverable to the previous version by re-running the previous image tag (retag in compose) and, if needed, restoring the SQLite database files from the latest pre-deploy backup.

#### Scenario: Code rollback
- **WHEN** the compose file is pointed at the previous image tag and the stack is restarted
- **THEN** the previous application version runs against the current database

#### Scenario: Data rollback
- **WHEN** database files are restored from a pre-deploy backup while the container is stopped
- **THEN** the application starts against the restored data

### Requirement: Backup confidentiality
Database backups created by the deploy pipeline contain special-category data (mood entries, notes, medications, episode history) and SHALL be readable only by the application owner. The backup directory SHALL have mode `0700`, and backup files SHALL NOT be readable by group or others. Encryption of backups at rest SHALL be applied when the backup leaves the NAS; the existing retention of the 10 most recent backups SHALL be preserved.

#### Scenario: Backup directory is private
- **WHEN** `bin/deploy.sh` creates a backup
- **THEN** the backup directory has mode `0700`
- **AND** the copied `goodmood.db*` files are not readable by group or others

#### Scenario: Backup retention preserved
- **WHEN** a deploy creates a backup and more than 10 backups exist
- **THEN** the oldest backups are deleted so that exactly 10 remain

#### Scenario: Backups stay out of the image and the repository
- **WHEN** the Docker image is built
- **THEN** no backup file is present in the image
- **AND** no backup file is tracked by git

### Requirement: Container network binding
The application container SHALL NOT be directly reachable from the local network. The published port SHALL be bound to `127.0.0.1`, so that all traffic from outside arrives through the TLS-terminating reverse proxy and no plaintext HTTP endpoint is exposed to the LAN.

#### Scenario: Published port is bound to loopback
- **WHEN** the compose stack is started
- **THEN** the published port listens on `127.0.0.1` only
- **AND** is not bound to `0.0.0.0`

#### Scenario: Direct request to the LAN address is refused
- **WHEN** a request is made to `http://<nas-lan-ip>:<published-port>`
- **THEN** the connection is refused

#### Scenario: Application reachable through the reverse proxy
- **WHEN** a request is made through the reverse proxy
- **THEN** the application responds with HTTP 200

