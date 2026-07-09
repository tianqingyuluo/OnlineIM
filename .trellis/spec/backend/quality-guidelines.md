# Quality Guidelines

> Code standards, conventions, and forbidden patterns for the backend.

---

## Dependency Injection

- **Constructor injection** is the default — do not write `@Autowired` on fields.
- Spring auto-detects single-constructor injection, so `@Autowired` on the constructor is optional.
- Some classes use `@Autowired` on constructors (`MessageServiceImpl`, `WebSocketMessageProcessor`, `RedisEventListener`, `RedisStreamService`) — this is acceptable but not required.
- **Never use field injection** (`@Autowired` on a field).

Reference: `service/impl/UserServiceImpl.java` (constructor injection without `@Autowired`)

---

## Stereotype Annotations

- `@Service` should be on the **implementation class**, not the interface.
- Current codebase has `@Service` on both interfaces and impls (`UserService` interface + `UserServiceImpl`). New services should only annotate the impl.
- `@RestController` for controllers, `@Repository` for MongoDB repositories, `@Mapper` for MyBatis mappers, `@Component` for everything else.

---

## Transactions

- `@Transactional` on service methods that perform **multiple MySQL writes**.
- `@Transactional` does **not** cover MongoDB operations — MongoDB writes outside the transaction boundary will not roll back.
- Do not add `@Transactional` to read-only methods unless using `@Transactional(readOnly = true)` for optimization.

Reference: `service/impl/GroupServiceImpl.java` — `createGroup`, `updateGroupByID`, `disbandGroupByID`, etc.

---

## ID Generation

- Generate IDs in the **service layer** using `cn.hutool.core.util.IdUtil.getSnowflakeNextIdStr()` with a type prefix.
- Do not generate IDs inside SQL/XML via `${}` OGNL expressions (see `GroupMapper.xml` antipattern).
- Document the ID prefix in the entity field comment.

```java
String gid = "grp_" + IdUtil.getSnowflakeNextIdStr();
```

---

## Thread Safety

- Services are **singletons** — do not store mutable state in instance fields.
- **`SimpleDateFormat` is not thread-safe** — `MessageServiceImpl` has a `SimpleDateFormat` instance field, which is a concurrency bug. Use `java.time.format.DateTimeFormatter` (immutable) or create `SimpleDateFormat` locally.
- `LocalSessionRegistry` correctly uses `ConcurrentHashMap` for session maps.

---

## JWT / Security

- **JWT secret key must be persistent** — `JwtUtil.SECRET_KEY = Keys.secretKeyFor(HS256)` is `static final` and regenerated on every JVM start, invalidating all tokens on restart and breaking multi-instance deployments. The secret must be loaded from configuration.
- **Never echo passwords** in responses — `AuthController.register` includes `password` in the response body.
- **CORS** is currently wide open (`*` origin patterns). Restrict to known origins in production.
- **CORS preflight must bypass auth filters** — `OPTIONS /**` should be permitted and JWT filters should pass `OPTIONS` through before reading `Authorization`, otherwise browsers report missing CORS headers instead of the real API error.
- **`db.properties` contains plaintext credentials** committed to the repository. Use environment variables or a secrets manager.

---

## WebSocket

- **Redis Stream consumption should use a consumer group** — currently `StreamOffset.fromStart("im:message:stream")` replays all historical messages on every restart with no ACK. Use `StreamMessageListenerContainer.receive(Consumer.from(...), StreamOffset.create(...), ...)` with consumer groups.
- **`UserSessionService.ip`** has `@Value` on a `final` field initialized to `""` — `@Value` field injection does not work on `final` fields. Use constructor injection.
- **`JwtUtil.getRemainingValidityTime`** returns the absolute expiration timestamp, not the remaining time — the method name is misleading and `VertxWebSocketServer` uses it as a TTL value incorrectly.

---

## Forbidden Patterns

| Pattern | Why | Reference |
|---------|-----|-----------|
| Field injection (`@Autowired` on field) | Hides dependencies, prevents testing | — |
| `@Service` on interface | Unnecessary, confusing | `service/UserService.java` |
| `SimpleDateFormat` as instance field in singleton | Not thread-safe | `service/impl/MessageServiceImpl.java` |
| `@Value` on `final` field | Silently fails | `service/UserSessionService.java` |
| `${}` in MyBatis SQL for ID generation | SQL injection risk (safe here but bad practice), bypasses Java logic | `resources/mapper/GroupMapper.xml` |
| `ErrorCodeUtil.getErrorOutput()` in controllers | Bypasses global exception handling | `controller/MessageController.java` |
| Manually parsing JWT in controllers | Duplicates filter authentication | `controller/MessageController.java` |
| Echoing passwords in responses | Security vulnerability | `controller/AuthController.java` |
| Plaintext credentials in committed files | Security vulnerability | `resources/db.properties` |
| `catch(Exception e) { throw new RuntimeException(e); }` | Loses original exception type and message | `service/impl/GroupServiceImpl.java` |

---

## Testing

- The `src/test/` directory contains **no effective automated tests** — only a context-load test and ad-hoc script files.
- Test files named `testRedis.java`, `Bcrypt.java`, `AuthController.java` do not follow JUnit conventions and will not be executed by Surefire.
- New tests should:
  - Follow `*Test.java` / `*Tests.java` naming.
  - Use JUnit 5 (`@Test`, `@SpringBootTest`, `@MockBean`, etc.).
  - Be placed in `src/test/java/icu/tianqingyuluo/onlineim/`.

### Scenario: Redis Stream / Testcontainers Integration Tests

#### 1. Scope / Trigger
- Trigger: Adding or changing Redis Stream / consumer-group behavior, or any Docker-backed integration test that must run under default `./mvnw test`.

#### 2. Signatures
- Test file discovery: `*Test.java` / `*Tests.java` only under default Surefire config.
- Backend verification command: `cd onlineIM-server && ./mvnw test`
- Redis Stream consumer-group registration under test:
  ```java
  container.receive(
      Consumer.from(groupId, consumerName),
      StreamOffset.create("im:message:stream", ReadOffset.lastConsumed()),
      listener
  )
  ```

#### 3. Contracts
- Integration tests that use Testcontainers must be named so they are discovered by default `mvn test` (`RedisStreamConsumerGroupIntegrationTest`, not `RedisStreamConsumerGroupIT`).
- If Docker daemon requires a higher API version than docker-java's default, pass the API version through Surefire (`<api.version>1.40</api.version>` in `maven-surefire-plugin`).
- If the environment cannot pull `testcontainers/ryuk`, disable Ryuk in Surefire environment variables (`TESTCONTAINERS_RYUK_DISABLED=true`).
- Redis test templates should use `StringRedisSerializer` for stream key/field compatibility with production stream usage.

#### 4. Validation & Error Matrix
- Test file named `*IT.java` only -> test is **not executed** by default `./mvnw test`.
- `src/test/` ignored by `.gitignore` -> test files are invisible to `git status` and can be omitted from commits.
- Docker API too old (`client version 1.32 is too old`) -> configure Surefire `api.version` to match daemon minimum.
- Ryuk pull timeout -> disable Ryuk for local/CI environments that cannot reach Docker Hub.

#### 5. Good / Base / Bad Cases
- Good: `RedisStreamConsumerGroupIntegrationTest.java` is executed by `./mvnw test` and validates broadcast + ack behavior against real Redis.
- Base: `RedisEventListenerTest.java` covers ack/no-ack branches with Mockito only.
- Bad: `RedisStreamConsumerGroupIT.java` exists but is never run by default, creating false confidence.

#### 6. Tests Required
- Unit: listener ack path, local-filter path, exception/no-ack path.
- Integration: two consumer groups both receive one stream message and ack independently.
- Integration: new group created with `$` does not replay historical messages and only consumes new ones.
- Integration or focused unit: `VertxWebSocketServer.stop()` destroys the consumer group on normal shutdown.
- Integration or focused config test: `RedisConfig.streamContainer(...)` wires `Consumer.from(...)` + `ReadOffset.lastConsumed()` correctly.

#### 7. Wrong vs Correct
##### Wrong
```java
// RedisStreamConsumerGroupIT.java
// Looks like an integration test, but default Surefire will skip it.
class RedisStreamConsumerGroupIT {
}
```

##### Correct
```java
// RedisStreamConsumerGroupIntegrationTest.java
// Still clearly an integration test, and default Surefire will execute it.
class RedisStreamConsumerGroupIntegrationTest {
}
```

---

## Build & Verification

```bash
# Compile (in onlineIM-server/)
./mvnw compile

# Run tests
./mvnw test

# Package
./mvnw package -DskipTests

# Run the application
./mvnw spring-boot:run
```

---

## Vert.x WebSocket Endpoint Contract

### 1. Scope / Trigger

- Trigger: changing `VertxWebSocketServer`, WebSocket authentication, heartbeat routing, or server integration tests.

### 2. Signatures

```java
@Value("${websocket.path:/ws}")
private String websocketPath;
```

### 3. Contracts

- The WebSocket server accepts upgrades only when the URI path exactly matches `websocket.path`; query parameters are excluded from the comparison.
- Path validation runs before token extraction and authentication.
- Heartbeat integration tests use a real Vert.x server on port `0` and a real Vert.x client; JWT and Redis boundaries may be mocked.

### 4. Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| URI path equals configured path | Continue to authentication |
| URI path differs | Reject upgrade with HTTP 404 |
| Token missing or invalid | Reject upgrade with HTTP 401 |
| Silent connection exceeds timeout | Close socket and remove local session |

### 5. Good / Base / Bad Cases

- Good: `/api/v1/chat?token=...` is accepted when `websocket.path=/api/v1/chat`.
- Base: valid connection sends `HEARTBEAT` and receives `HEARTBEAT_ACK`.
- Bad: a global `webSocketHandler` accepts `/wrong` because the configured path is only logged, not checked.

### 6. Tests Required

- Real WebSocket heartbeat request receives `HEARTBEAT_ACK`.
- Real silent connection is closed and removed by `cleanupInactiveSessions`.
- Upgrade on a non-configured path is rejected.

### 7. Wrong vs Correct

#### Wrong

```java
.webSocketHandler(ws -> handleWebSocketConnection(ws, ws.uri()))
// websocketPath is never checked
```

#### Correct

```java
String requestPath = uri.split("\\?", 2)[0];
if (!websocketPath.equals(requestPath)) {
    ws.reject(404);
    return;
}
```
