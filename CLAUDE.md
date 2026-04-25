# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
./gradlew build                  # Build + run all checks (unit tests, integration tests, detekt)
./gradlew test                   # Unit tests only
./gradlew integrationTest        # Integration tests only (requires Docker for Testcontainers)
./gradlew detekt                 # Static analysis
./gradlew detekt -PautoCorrect=true  # Static analysis with auto-fix
./gradlew generateApi            # Regenerate Fabrikt API code from bundled OpenAPI spec
./gradlew jooqCodegen            # Regenerate jOOQ classes from live DB schema (requires DB at localhost:5432)
./gradlew run                    # Run the application locally
./contracts/lint.sh              # Lint OpenAPI contracts (requires Docker)
./contracts/build.sh             # Bundle contracts/ → src/main/resources/openapi/todo.yaml (requires Docker)
./gradlew test --tests "com.example.todo.repository.mappers.TodoMapperTest"  # Single test class
./gradlew dependencies --write-locks  # Regenerate gradle.lockfile after dependency changes
```

Integration tests use Testcontainers (PostgreSQL + Valkey + dbmate for migrations), so Docker must be running.

## Running Locally

```bash
# Start dependencies (DB, Valkey, run migrations)
docker compose up db valkey dbmate

# Run the app
./gradlew runFix db-codegen, so it uses init-db.sh
```

Or run the full stack with `docker compose up`. App starts on `http://localhost:8080`.

## Architecture

**Ktor + Koin + jOOQ** web service with an API-first (OpenAPI) approach.

### Code Generation (do not edit `src/generated/`)

- **API layer**: The OpenAPI source lives in `contracts/todo/` as a multi-file spec. `src/main/resources/openapi/todo.yaml` is the committed bundle — do not edit it directly. After changing contract source files, run `./contracts/build.sh` to rebundle, then `./gradlew generateApi` to regenerate Kotlin code. Generated models use `Contract` suffix.
- **Database layer**: jOOQ generates table/record classes from a live PostgreSQL schema. Run `./gradlew jooqCodegen` (requires running DB at localhost:5432).

### Layered Architecture (per feature module, e.g. `todo/`)

1. **Controller** (`api/TodoController.kt`) — Implements generated Fabrikt controller interface. Maps between API contracts and domain types. Converts service errors to `ProblemDetailsException` (RFC 9457 Problem Details).
2. **Service** (`services/TodoService.kt`) — Business logic. Uses `kotlin-result` (`Result<T, ServiceError>`) for typed error handling. Manages transactions via `resultTransactionCoroutine`.
3. **Repository** (`repository/TodoRepository.kt`) — jOOQ queries returning `RepositoryResult<T>`. Uses coroutine-based async execution via R2DBC.
4. **Mappers** — Separate mapper objects for contract↔domain (`api/mappers/`) and domain↔record (`repository/mappers/`).

### Key Patterns

- **Result types everywhere**: Repository returns `RepositoryResult<T>`, service returns `TodoServiceResult<T>`. Errors are mapped between layers (repository errors → service errors → HTTP exceptions). Uses `kotlin-result` library, not stdlib.
- **`resultTransactionCoroutine`** (`TransactionExtensions.kt`): Custom extension that wraps jOOQ's `transactionCoroutine` to automatically rollback on `Result` failure.
- **Repository helpers** (`core/repository/RepositoryErrorHandling.kt`): `runWrappingError` wraps jOOQ exceptions into `RepositoryError`, `mapExpectingOne` validates row counts, `toNotFoundIfNull` converts nulls, `toNullIfNotFound` and `ignoreNotFound` suppress not-found errors.
- **Koin modules**: Each feature defines a Koin `module` (e.g. `todoModule`). All modules are assembled in `config/Koin.kt`. Infrastructure modules (database, redis, monitoring) are in `config/`.

### Testing

- **Unit tests** (`src/test/`): Kotest `FunSpec` style. No special setup needed.
- **Integration tests** (`src/integrationTest/`): Use Ktor's `testApplication` with a custom `integrationTestModule` that wires up Testcontainers (PostgreSQL, Valkey) and runs dbmate migrations. `IntegrationTestBase` provides helpers for creating authenticated test clients with injected sessions. Each test truncates tables in `beforeEach`.

### Database Migrations

Managed by [dbmate](https://github.com/amacneil/dbmate). Migration files in `db/migrations/`. Schema dump in `db/schema.sql`.

```bash
dbmate new <name>  # Create a new migration file
dbmate up          # Apply pending migrations
```

## Style

- Max line length: 120 characters
- Detekt config: `config/detekt.yml`
- Max 2 return statements per function (excluding lambdas)
- Max 5 function parameters, 6 constructor parameters
- Wildcard imports banned except `io.ktor.*`
