# deployment Spec (delta)

## ADDED Requirements

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
