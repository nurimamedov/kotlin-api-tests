# Kotlin API tests: one order, three observations

A small, original portfolio fixture for testing a fictional order API. A test checks one operation at three boundaries: the HTTP response, the PostgreSQL row, and the RabbitMQ event. Everything is synthetic; no shared services or external credentials are needed.

```text
                           INSERT order by UUID
                          ┌─────────────────── PostgreSQL
POST /orders -> fixture --|
                          └─────────────────── RabbitMQ direct exchange
                            publish orders.created + same UUID
```

## What is checked

- A valid JSON request returns HTTP 201 and the assigned UUID. The test reads PostgreSQL by that UUID and verifies the stored values.
- RabbitMQ receives an event on the expected routing key with the same UUID in both the payload and correlation ID. An unrelated binding must receive nothing.
- Malformed inputs and out-of-range quantities return HTTP 400 without calling storage or publishing. These tests need no container runtime.
- The integration test owns its containers, database and exchange. It polls for the event with a timeout instead of sleeping for a fixed delivery delay. The HTTP server binds to loopback on an OS-assigned port.

## Run

With Java 17 and Maven 3.9+:

```sh
mvn test                         # Fast API tests; no container daemon
mvn -Dtest=OrderFlowIT test      # Focused PostgreSQL + RabbitMQ run; Docker required
mvn -Dtest=OrderHttpTest,OrderFlowIT test
```

The first integration run may download container images. JUnit writes its reports to `target/surefire-reports/`; the Allure results are in `target/allure-results/` (the CI workflow uploads both). With the Allure CLI installed, run `allure serve target/allure-results` to view the results.

## Scope

The server is a test fixture, not a production order service. Persistence and publishing are separate operations. If the broker fails after the database commit, the fixture reports a dependency error and the row can still exist. In a production version I would persist an outbox entry in the database transaction and make publication retryable and consumers idempotent; the test here intentionally does not claim atomic delivery.

## Optional Gatling load smoke

```sh
mvn -Pload -B gatling:test
```

This experiment increases the input rate in three five-second steps (2, 5 and
10 requests/s). Gatling's report under `target/gatling/` includes p95 and p99
for the measured run. The scenario checks HTTP 201 and a response ID for every
request. It uses the same loopback fixture but no-op storage and messaging, so
the measured latency covers the HTTP fixture only. It is neither a production
benchmark nor a database or broker throughput test.

## License

[MIT](LICENSE). Copyright © 2026 Nuri Mamedov. Keep the copyright and license notice when copying or redistributing substantial portions.
