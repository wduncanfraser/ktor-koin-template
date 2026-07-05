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
docker compose up db valkey dbmate openfga fga-provision

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
- **Koin modules**: Each feature defines a Koin `module` (e.g. `todoModule`, `todoListModule`). All modules are assembled in `config/Koin.kt`. Infrastructure modules (database, redis, monitoring) are in `config/`.

### Authorization (OpenFGA / ReBAC)

Access control is relationship-based (ReBAC) via [OpenFGA](https://openfga.dev/), not ownership columns in the database. `fga/authorization-model.fga` is the source of truth for the current model — read it directly rather than trusting a copy here. It's provisioned into a running store by the `fga-provision` service in `docker compose up`, and by equivalent Testcontainers in integration tests (`IntegrationTestBase`'s `openfga`/`fgaProvision`).

Relation assignment (`owner`/`editor`/`viewer`) happens only at the `todo_list` level today — a `todo` has no relations of its own, so its `can_read`/`can_write`/`can_delete` are entirely inherited from its parent list; it can't be shared independently. That's a template simplification, not a limitation of the approach: direct per-todo relations could be added later purely as a model change (e.g. `owner`/`editor`/`viewer` on `type todo`, unioned with the inherited ones via `... or can_read from parent_list`) — no service-layer changes are required, since permission checks already scope to the specific `AuthorizationResource.Todo` being acted on and tuple writes (`AuthorizationTuple.UserRelation`) already work against any `AuthorizationResource`.

Core types (`core/authorization/`):

- **`AuthorizationService`** — interface with `check`, `writeTuples`, `deleteTuples`; backed by `OpenFgaAuthorizationService`.
- **`AuthorizationResource`** — sealed class identifying what's being checked (`TodoList`, `Todo`).
- **`Permission`** — sealed interface; `Permission.Common` (`CAN_READ`/`CAN_WRITE`/`CAN_DELETE`) covers the relations every resource type defines. Feature-specific permissions (e.g. `can_share`) would implement `Permission` directly.
- **`AuthorizationTuple`** — `UserRelation` (user → resource, e.g. owner) or `ResourceRelation` (resource → resource, e.g. a todo's `parent_list` link).
- **`AuthorizationError`** — `NotFound` (404, no access at all) vs `Forbidden` (403, can view but lacks the specific permission); `check` distinguishes these by falling back to a `CAN_READ` check when the requested permission is denied. `WriteFailed` means a `writeTuples`/`deleteTuples` call itself failed — an infrastructure error, not a permission outcome.

Service methods call `authorizationService.check(userId, permission, resource)` before reading or mutating, and bind `writeTuples`/`deleteTuples` results into the surrounding `resultTransactionCoroutine` so a failed tuple write rolls back the whole operation — a resource is never left committed without its ownership tuple.

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
