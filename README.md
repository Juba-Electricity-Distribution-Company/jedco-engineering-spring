# JEDCO Engineering

Spring Boot application for managing electricity distribution engineering data: transformers and loading readings, feeders, low-voltage (LV) poles and meters, commissioning, meter changes and relocations, and user roles and permissions. The application serves a REST API and a bundled web frontend.

## Stack

- Java 21 and Spring Boot 3.4.5
- Maven wrapper (Maven 3.9.5)
- Spring Web, Spring Data JPA / Hibernate, and Microsoft SQL Server
- Spring Security, BCrypt passwords, and JWT authentication (JJWT)
- Lombok and MapStruct annotation processing
- Springdoc OpenAPI UI and Proj4J coordinate conversion

## Prerequisites

Install JDK 21 and ensure `JAVA_HOME` points to it. The Maven wrapper downloads Maven and dependencies on first use. On Windows, use `mvnw.cmd` instead of `./mvnw`.

You need access to a development SQL Server database with the existing application schema and reference data. Hibernate uses `ddl-auto=validate`: it checks the schema but does not create or migrate it. This repository contains no database migration scripts, seed scripts, or automated initial administrator setup. Obtain the development database and an account through the project maintainer.

## Configuration

Provide these environment variables before starting the application:

| Variable | Purpose |
| --- | --- |
| `ENGINEERING_DB_URL` | SQL Server JDBC URL, such as `jdbc:sqlserver://localhost:1433;databaseName=engineering;encrypt=true` |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `ENGINEERING_JWT_KEY` | Base64-encoded signing key suitable for HS256 (at least 32 random bytes) |
| `SSL_KEY_STORE_PASSWORD` | Password for the configured PKCS12 keystore when HTTPS is enabled |

Use environment variables or external configuration for credentials; do not commit them. A `.env` file is not automatically loaded by this application. For a local-only JWT key, a shell with OpenSSL can use:

```bash
export ENGINEERING_JWT_KEY="$(openssl rand -base64 32)"
```

The checked-in configuration enables HTTPS on port `8084`, with a bundled `jedcopower.p12` keystore and alias `jedcopower`. Supply the matching password or configure your own keystore through Spring Boot's `SERVER_SSL_*` environment variables.

Logging defaults to `/var/log/engineering/info/app-info.log` and `/var/log/engineering/error/app-error.log`, with daily rotation and 30-day retention. The service account must be able to write to these directories. For local development, the command below selects Spring Boot's console logging configuration and disables TLS:

```bash
# Set the database variables and ENGINEERING_JWT_KEY first.
export SERVER_SSL_ENABLED=false
export LOGGING_CONFIG=classpath:org/springframework/boot/logging/logback/base.xml
./mvnw spring-boot:run
```

With these local overrides, open `http://localhost:8084/`. With the default TLS settings, use `https://localhost:8084/` and a client that trusts the configured certificate.

## Build and test

```bash
# Compile, including Lombok and MapStruct generated code.
./mvnw compile

# Run tests with development database and runtime configuration available.
./mvnw test

# Test and build an executable JAR.
./mvnw clean package

# Package without running tests when runtime dependencies are unavailable.
./mvnw -DskipTests package

# Run the packaged application with the same environment configuration.
java -jar target/jedco-engineering-spring-0.0.1-SNAPSHOT.jar
```

The test suite includes isolated pole-data authorization tests and a `@SpringBootTest` context-load test. The context-load test starts the application context and requires a reachable database with a matching schema, the required configuration, and usable logging settings. There is no isolated test database or test profile. Skipping tests does not verify application startup.

## API

`MvcConfig` adds the case-sensitive `/Engineering` prefix to REST controllers.

| Base path | Responsibility |
| --- | --- |
| `/Engineering/auth` | Login (`POST`) and token refresh (`POST /refresh`) |
| `/Engineering/txData` | Transformer, feeder, pole, loading reading, and box-number operations |
| `/Engineering/lvData` | LV data lookup, registration, and updates |
| `/Engineering/commissioning` | Commissioning, transformer registration, meter changes, relocations, and LV extensions |
| `/Engineering/users` | User accounts, profiles, passwords, and account status |
| `/Engineering/UserRole` | User role updates and listing |
| `/Engineering/UserAction` | Available user actions |
| `/Engineering/roleDefinition` | Role definitions and assigned actions |

Login accepts JSON with `username` and `password`. Authenticated requests use `Authorization: Bearer <token>`. Springdoc provides the Swagger UI at `/swagger-ui/index.html` and OpenAPI JSON at `/v3/api-docs`.

The HTTP security configuration currently permits all URL patterns; authorization is enforced on methods that declare `@PreAuthorize`. Not every endpoint has that annotation. JWT access tokens default to three years and refresh tokens to seven days. These describe the current implementation, not a guarantee that all endpoints require authentication.

`GlobalExceptionHandler` returns HTTP 200 with a failure payload for the application's `AuthenticationException` and `ResponseException`. Clients must inspect the response body's status as well as the HTTP status.

### Pole-data authorities

Existing `REGISTER_LV_DATA` role assignments retain access to all existing LV operations. Granular authorities are alternatives for these routes (all under `/Engineering/lvData`):

| Routes | Granular authority |
| --- | --- |
| `GET /getDataByUser`, `/getDataByTx`, `/getDataByFeederTxPole`, `/getDataByPoleNo` | `VIEW_POLE_DATA` |
| `POST /registerLvData` | `REGISTER_POLE_DATA`; also `REGISTER_METER_DATA` when the request includes meters |
| `POST /updateLvData` | `UPDATE_POLE_DATA` for pole/remark edits, existing meter edits, or removals; `REGISTER_METER_DATA` for new meters (null meter ID) |

For granular users, combined requests require each applicable authority. A meter-only update must submit unchanged pole fields and retain unchanged existing meters; it cannot edit or remove existing data. Routes, DTOs, and responses are unchanged. Shared lookup endpoints that previously had no method authorization keep their existing access rules, as do commissioning and box-number operations.

Authorities are loaded from `jd_user_action.action_name` through existing `jd_role_definition` role assignments. Provision the four new action names in the externally managed database, with the appropriate action group/status and role assignments. Do not remove or rename `REGISTER_LV_DATA` or automatically grant new actions to existing roles. No schema change is required; this application does not seed these records.

Run the isolated authorization tests without a database:

```bash
./mvnw -Dtest=LvDataAuthorizationTests,PoleDataAuthorizationTests test
```

## Source layout

Java sources are under `src/main/java/com/jedco/jedcoengineeringspring/`:

| Directory | Contents |
| --- | --- |
| `controllers/` | REST mappings and method-level authorization |
| `services/` | Service interfaces, implementations, and business workflows |
| `repositories/` | Spring Data JPA repositories and queries |
| `models/` | Entities mapped to the existing SQL Server schema |
| `rest/request/`, `rest/response/` | API request and response DTOs |
| `mappers/` | MapStruct entity-to-DTO mappings |
| `config/` | Security, JWT filter, MVC prefix, OpenAPI, and exception handling |
| `exceptions/`, `Util/` | Application exceptions and date utilities |

`src/main/resources/` holds application and logging configuration, the TLS keystore, and `static/` web assets. The static directory contains compiled JavaScript/CSS and source maps; no frontend package manifest or standalone frontend source project is included. Rebuild frontend changes in its source project and replace the distribution assets together.

See [AGENTS.md](AGENTS.md) for repository-specific coding and verification guidance.
