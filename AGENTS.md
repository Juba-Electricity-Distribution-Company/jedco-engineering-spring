# Repository guidance

## Scope and orientation

These instructions apply throughout this repository. Read `README.md` and the relevant source before making changes. This is a Java 21 / Spring Boot 3.4.5 Maven application backed by an existing SQL Server schema, with a compiled frontend bundled in resources.

The main package is `com.jedco.jedcoengineeringspring`. Follow the existing flow: controllers → service interfaces and implementations → repositories and entities. API DTOs live in `rest/request` and `rest/response`; MapStruct interfaces live in `mappers`.

## Implementation conventions

- Match nearby Java formatting and naming; use constructor injection and existing Lombok patterns where appropriate.
- Keep HTTP handling in controllers and business rules in services. Use the existing Spring Data repositories for persistence.
- Preserve DTO field names, route casing, and response shapes unless the task explicitly changes the API. The bundled frontend depends on these contracts.
- All REST controllers receive `/Engineering` through `MvcConfig`; do not duplicate that prefix in controller mappings.
- Update MapStruct interfaces when mappings change. Do not edit generated implementations in `target/`; Maven annotation processing creates them.
- Preserve entity table/column names and relationships. Hibernate validates an externally managed schema using `PhysicalNamingStrategyStandardImpl`; do not switch to `create` or `update` to bypass mismatches. Describe any required database change explicitly.
- Review transaction boundaries and history records when changing multi-entity meter, commissioning, or LV workflows.
- Keep changes focused; avoid unrelated formatting, dependency upgrades, or generated-asset churn.

## Authentication and response contracts

- Inspect `SecurityConfiguration`, `JwtAuthenticationFilter`, and nearby `@PreAuthorize` annotations when changing endpoints. URL-level security currently permits `/**`; method annotations are significant. Do not assume a new endpoint is protected automatically.
- Preserve action authority names and role relationships unless the task requires changing them.
- `GlobalExceptionHandler` represents application authentication and response failures as HTTP 200 failure payloads. Treat changing this behavior as an API contract change.
- JWT signing uses a Base64-decoded key and HS256. Runtime configuration and token lifetime settings are in `application.properties` and `JwtServiceImpl`.
- Keep credentials, JWTs, signing keys, and keystore passwords out of code, documentation examples, and logs. Do not replace the bundled keystore as an incidental change.

## Build and validation

Use the checked-in Maven wrapper with JDK 21:

```bash
./mvnw compile
./mvnw test
./mvnw -DskipTests package
```

Choose verification appropriate to the change. Compilation checks annotation processing; packaging with `-DskipTests` does not establish that tests pass. For behavior changes, add focused tests covering the changed behavior and relevant failure cases. Prefer isolated service or controller tests where database integration is unnecessary.

The existing `@SpringBootTest` context-load test needs a configured development SQL Server database with the expected schema. There are no migration scripts, seed scripts, or dedicated test profile. Never point automated tests at a production database or change schema validation merely to get tests passing.

Required environment variables are `ENGINEERING_DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `ENGINEERING_JWT_KEY`; HTTPS also needs `SSL_KEY_STORE_PASSWORD`. For local execution, `SERVER_SSL_ENABLED=false` disables TLS and `LOGGING_CONFIG=classpath:org/springframework/boot/logging/logback/base.xml` avoids the default `/var/log/engineering` file appenders. See the README for complete setup.

For documentation-only edits, verify statements and paths against the repository and check whitespace; application startup is unnecessary. Before finishing, review the diff, run `git diff --check`, and report what was verified and any checks blocked by missing configuration or infrastructure.

## Frontend and generated files

`src/main/resources/static/` is a compiled web distribution, including hashed bundles and source maps. This repository has no frontend `package.json` or standalone source project. Do not hand-edit minified bundles or invent an npm build command. Make frontend changes in its source project when available, then copy the rebuilt distribution as a consistent set.

Do not commit `target/`, generated mapper implementations, local IDE settings, runtime logs, or local secret configuration. Update `README.md` when setup, commands, configuration, or externally visible behavior changes.
