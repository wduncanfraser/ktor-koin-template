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

Integration tests use Testcontainers (PostgreSQL + Valkey + OpenFGA + dbmate for migrations), so Docker must be running.

## Running Locally

```bash
# Start dependencies (DB, Valkey, OpenFGA, run migrations)
docker compose up db valkey dbmate openfga-migrate openfga fga-provision

# Run the app
./gradlew run
```

Or run the full stack with `docker compose up`. App starts on `http://localhost:8080`.

## Architecture

**Ktor + Koin + jOOQ** web service with an API-first (OpenAPI) approach.

### Code Generation (do not edit `src/generated/`)

- **API layer**: The OpenAPI source lives in `contracts/todo/` as a multi-file spec. `src/main/resources/openapi/todo.yaml` is the committed bundle — do not edit it directly. After changing contract source files, run `./contracts/build.sh` to rebundle, then `./gradlew generateApi` to regenerate Kotlin code. Generated models use `Contract` suffix.
- **Database layer**: jOOQ generates table/record classes from a live PostgreSQL schema. Run `./gradlew jooqCodegen` (requires running DB at localhost:5432).

### Layered Architecture (per feature module, e.g. `todo/`, `todolist/`)

1. **Controller** (`api/TodoListController.kt`) — Implements generated Fabrikt controller interface. Maps between API contracts and domain types. Converts service errors to `ProblemDetailsException` (RFC 9457 Problem Details).
2. **Service** (`services/TodoListService.kt`) — Business logic. Uses `kotlin-result` (`Result<T, ServiceError>`) for typed error handling. Manages transactions via `resultTransactionCoroutine`.
3. **Repository** (`repository/TodoListRepository.kt`) — jOOQ queries returning `RepositoryResult<T>`. Uses coroutine-based async execution via R2DBC.
4. **Mappers** — Separate mapper objects for contract↔domain (`api/mappers/`) and domain↔record (`repository/mappers/`).

### Key Patterns

- **Result types everywhere**: Repository returns `RepositoryResult<T>`, service returns a module-specific result type (e.g. `TodoListServiceResult<T>`). Errors are mapped between layers (repository errors → service errors → HTTP exceptions). Uses `kotlin-result` library, not stdlib.
- **`resultTransactionCoroutine`** (`TransactionExtensions.kt`): Custom extension that wraps jOOQ's `transactionCoroutine` to automatically rollback on `Result` failure.
- **Repository helpers** (`core/repository/RepositoryErrorHandling.kt`): `runWrappingError` wraps jOOQ exceptions into `RepositoryError`, `mapExpectingOne` validates row counts, `toNotFoundIfNull` converts nulls, `toNullIfNotFound` and `ignoreNotFound` suppress not-found errors.
- **Pagination** (`core/repository/PaginationUtil.kt`): Shared helpers `calculateTotalPages` and `calculateOffset` used by all paginated repository queries.
- **Validation** (`core/validation/CommonFieldRules.kt`): Shared konform field rules (e.g. `itemName()`) reused across feature modules. Module-specific rules live in `<module>/validation/`.
- **Koin modules**: Each feature defines a Koin `module` (e.g. `todoModule`, `todoListModule`). All modules are assembled in `config/Koin.kt`. Infrastructure modules (database, redis, monitoring, tracing) are in `config/`.
- **Observability**: Metrics (Micrometer→Prometheus, `/metrics`) and health (Cohort, `/health`) are in `config/Monitoring.kt`. Distributed tracing is OpenTelemetry, in `config/Tracing.kt` — a single autoconfigured `OpenTelemetry` (driven by `OTEL_*` env vars) injected via Koin into Ktor server/client, R2DBC, and Lettuce instrumentation, plus hand-rolled spans in `OpenFgaAuthorizationService` (the OpenFGA SDK isn't auto-instrumented). `configureTracing()` must run before other logging/telemetry plugins. Logs carry the active `trace_id` via the logback OpenTelemetry MDC appender. Span **export is off by default** (`otel.traces.exporter=none` in `config/Tracing.kt`) — no tracing backend is shipped; enable with `OTEL_TRACES_EXPORTER=otlp` + `OTEL_EXPORTER_OTLP_ENDPOINT`. The app runs with `-Dio.ktor.internal.disable.sfg=true` (build.gradle.kts, Dockerfile, test tasks) to work around KTOR-6802 — a Ktor SuspendFunctionGun ThreadLocal leak on Netty that otherwise merges independent requests into one trace; `TracingLeakNettyTest` guards it. See the README's Observability section.

### Authorization (OpenFGA / ReBAC)

Access control is relationship-based (ReBAC) via [OpenFGA](https://openfga.dev/), not ownership columns in the database. `fga/authorization-model.fga` is the source of truth for the model — read it directly. The README's Authorization section covers the conceptual model and the production-scaling caveats; this section is the in-code reference. The store/model are provisioned by the `fga-provision` service in `docker compose up`, and by equivalent Testcontainers in integration tests (`IntegrationTestBase`). In compose, OpenFGA persists to Postgres (its own `openfga` database via `db/dev/init-db.sh`); `openfga-migrate` creates OpenFGA's schema and `fga-provision` (`fga/provision/`) is idempotent — it creates the `todo` store only if absent, so repeated `up` doesn't duplicate stores.

Relations (`owner`/`editor`/`viewer`) are assigned only at the `todo_list` level; a `todo` inherits `can_read`/`can_write`/`can_delete` from its parent list and can't be shared independently. Adding per-todo sharing is a pure model change (add relations to `type todo`, union with the inherited ones) — no service code changes, since checks already scope to the specific `AuthorizationResource` and tuple writes are generic.

Core types (`core/authorization/`):

- **`AuthorizationService`** — interface (`check`, `listResourceIds`, `writeTuples`, `deleteTuples`, `deleteAllTuplesFor`), backed by `OpenFgaAuthorizationService`. `listResourceIds` backs the list endpoints (`GET /todo-lists`, `GET /todos`) so they return everything the caller can read, not just what they created; its scaling limits and the production alternative are on the method KDoc and in the README.
- **`deleteAllTuplesFor(resource)`** removes *every* tuple involving a resource when it's destroyed (OpenFGA has no cascade/pattern-delete). `deleteTodoList` uses it; `deleteTodo` stays an explicit single-tuple delete and must be updated if per-todo relations are ever added. Ordering, chunking, and cross-store (Postgres + OpenFGA) partial-failure semantics are on the method KDoc.
- **`AuthorizationResource`** — sealed class identifying what's being checked (`TodoList`, `Todo`). **`AuthorizationResourceType`** mirrors it without an id (`listResourceIds` needs a type to query, not an id it doesn't have yet).
- **`Permission`** — sealed interface; `Permission.Common` (`CAN_READ`/`CAN_WRITE`/`CAN_DELETE`) covers the relations every resource type defines. Feature-specific permissions (e.g. `can_share`) would implement `Permission` directly.
- **`AuthorizationTuple`** — `UserRelation` (user → resource, e.g. owner) or `ResourceRelation` (resource → resource, e.g. a todo's `parent_list` link).
- **`AuthorizationError`** — `NotFound` (404, no access at all) vs `Forbidden` (403, can view but lacks the specific permission); `check` distinguishes these by falling back to a `CAN_READ` check. `CheckFailed`/`WriteFailed` are infrastructure failures, not permission outcomes.

Service methods `check` before reading or mutating (check first, then hit the DB), and bind `writeTuples`/`deleteTuples` into the surrounding `resultTransactionCoroutine` so a failed tuple write rolls back the whole operation — a resource is never left committed without its ownership tuple.

### Testing

- **Unit tests** (`src/test/`): Kotest `FunSpec` style. No special setup needed. Detekt runs on test sources as well as main.
- **Integration tests** (`src/integrationTest/`): Use Ktor's `testApplication` with a custom `integrationTestModule` that wires up Testcontainers (PostgreSQL, Valkey, OpenFGA) and runs dbmate migrations. `IntegrationTestBase` provides helpers for creating authenticated test clients with injected sessions. Each test truncates tables in `beforeEach`.

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
