# E2E Test Suits

We use k6 for E2E testing: Both for user journeys and load testing.

## Requirements

- [k6](https://grafana.com/docs/k6/latest/set-up/install-k6/)
- [openapi-to-k6](https://github.com/grafana/openapi-to-k6?tab=readme-ov-file#getting-started)

## Client Generation

We use the following for generating k6 http clients from OpenAPI specifications: [openapi-to-k6](https://github.com/grafana/openapi-to-k6)

Example:

```bash
openapi-to-k6 ../src/main/resources/openapi/todo.yaml generated/
```

## Load Test

Our load test does a set order of CRUD operations and cleans up all data it creates so it can theoretically be run in 
any environment.

Default configuration is for 500 virtual users running the full workload every second for 1 minute.

Example:

```bash
k6 run -e BASE_URL="http://localhost:8080/api/v1" load-test.ts
```
