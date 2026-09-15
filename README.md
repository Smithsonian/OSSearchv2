# OSSearch

## Testing

### Prerequisites

- **JDK 21.** The build sets `<java.version>21</java.version>`; anything older fails to
  compile. If your default `java` is a different major version, point `JAVA_HOME` at a JDK 21
  installation for the commands below (for example
  `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` on macOS).
- **The Nutch plugins must be installed to your local Maven repository first.** They are a
  separate reactor and `ossearch-crawler` resolves them as ordinary dependencies:

  ```
  cd nutch-plugins && mvn install -DskipTests
  ```

### Running the backend tests

```
./mvnw -pl ossearch-crawler test -Dossearch.skipTests=false
```

`-Dossearch.skipTests=false` is required. A plain `mvn test` or `./mvnw test` **silently skips
every test** and still reports `BUILD SUCCESS`.

### Why the property is `ossearch.skipTests`, not `skipTests`

Surefire is configured in the root `pom.xml`'s `pluginManagement`, so its configuration is
inherited by *every* module in the reactor, including the legacy `nutch-plugins/*` modules
whose tests are already broken. Tests are skipped by default there for that reason.

If the skip flag were bound to Surefire's own well-known `skipTests` property, then passing
`-DskipTests=false` to run the `ossearch-crawler` tests would also un-skip the legacy modules
and break the build. The project-specific `ossearch.skipTests` name avoids that collision, so
an explicit `-Dossearch.skipTests=false` opts in only the module you actually asked for. See
the comment on the `ossearch.skipTests` property in the root `pom.xml` for the authoritative
version of this explanation.

### Database-backed integration tests (opt-in)

Most tests run without a database. The exception is
`BackupJobMySqlIntegrationTest`, which exercises the hand-written native SQL behind the
scheduled-backup lease and run-status tables — `INSERT IGNORE`, the
`DATE_ADD(..., INTERVAL :seconds SECOND)` expiry arithmetic, and the derived
`findFirstByOrderByStartedAtDescIdDesc` query. None of that can be verified against a mock,
and the rest of the backup suite mocks those repositories.

It is skipped unless you ask for it, so a normal run on a machine with no database still
passes. To run it against the `docker-compose` MySQL:

```
docker compose up -d db
```
```
./mvnw -pl ossearch-crawler test -Dossearch.skipTests=false -Dossearch.it.mysql=true
```

It is safe to point at a populated database: every test is transactional and rolls back, and
it runs with `ddl-auto=none` so it cannot alter the schema. The connection details are in the
test's `@TestPropertySource` and default to the `docker-compose` database on port 3309.
