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
