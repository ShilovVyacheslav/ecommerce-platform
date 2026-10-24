# Logging

Services write JSON to stdout; nothing in the application talks to the log pipeline.

```
stdout → vector-agent → Kafka "platform-logs" → vector-aggregator → Loki → Grafana ("ecommerce-platform · Logs")
```

Config: shared template `com/shilov/ecommerce/logging/logback-base.xml` (root `src/main/resources`), Vector and Loki in
`monitoring/`, Kubernetes in `k8s/logging/`. Start locally with `docker compose --env-file .env.docker --profile logging up -d`.

## Fields

| Field | Notes |
|---|---|
| `@timestamp`, `level`, `logger`, `thread`, `message` | message ≤ 4096 chars |
| `service`, `version`, `env`, `host` | `env` from `APP_ENVIRONMENT` |
| `correlationId` | `X-Correlation-Id` / `x-correlation-id`, created or validated at every entry point, carried through the outbox into Kafka headers |
| `traceId`, `spanId` | Micrometer Tracing (W3C `traceparent`), not exported |
| `action` | `http:OrderController.createOrder`, `grpc:ProductService/ReserveStock`, `kafka:order-events`, `job:…`, `gateway:<route>` |
| `userId` / `targetUserId` | numeric id only: acting user / affected user |
| `errorType`, `errorRootCause`, `errorHash`, `stackTrace` | stack trace ≤ 8192 chars, root cause first |

Shared business field names: `orderId`, `paymentId`, `productId`, `durationMs`, `httpStatus`. Only `service`, `level`
and `env` are Loki labels; everything else is queried with `| json`.

## Rules

- **ERROR** — a human must act (failed compensation, unhandled exception, record sent to DLT).
  **WARN** — handled failure or degradation (remote call failed, 5xx, slow request).
  **INFO** — one line per business fact and one access line per request. **DEBUG** — intermediate steps.
- Expected outcomes (404, 409, insufficient stock, declined card) are INFO. Log an exception once, where it is handled.
- Arguments must match `{}` placeholders (a trailing exception is allowed); extra fields go through `addKeyValue()`.
- Never log secrets, personal data (username, e-mail) or request/message payloads. Known secret fields and JWTs are
  masked, but that is a safety net.
- Thread pools must propagate the MDC (`MdcTaskDecorator`).

```java
log.info("Payment {} for order {} {}", v("paymentId", id), v("orderId", orderId), v("paymentStatus", status));
log.atInfo().addKeyValue("passwordChanged", changed).log("User {} updated", v("targetUserId", userId));
```

## Operations

| | |
|---|---|
| Readable output | `APP_LOGGING_FORMAT=text` (default in `local` profile and tests) |
| Change a level | `LOGGING_LEVEL_<LOGGER>=DEBUG` |
| Only failed/slow requests in the access log | `LOGGING_LEVEL_COM_SHILOV_ECOMMERCE_ACCESS=WARN` |
| Limits | `APP_LOGGING_ACCESS_SLOW_REQUEST_THRESHOLD`, `APP_LOGGING_MAX_MESSAGE_LENGTH`, `APP_LOGGING_ASYNC_QUEUE_SIZE` |
| Test the agent transform | `vector test monitoring/vector/vector-agent.yaml monitoring/vector/tests/vector-agent.tests.yaml` |

```logql
{env=~".+"} | json | correlationId="<id>"                                  # one request across all services
{service="order-service", level="ERROR"} |= "REQUIRES MANUAL REVIEW"        # orders needing manual review
topk(10, sum by (service, errorType, errorHash) (count_over_time({level="ERROR"} | json [24h])))
```
