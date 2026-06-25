# Logging Guidelines

> Structured logging conventions for the backend.

---

## Logging Framework

- **SLF4J via Lombok** `@Slf4j` annotation on classes that need logging.
- Spring Boot's default Logback backend.
- No structured logging framework (e.g. JSON log format) is configured — logs are plain text to console.

Reference: `controller/AuthController.java`, `exception/GlobalExceptionHandler.java`, `websocket/server/VertxWebSocketServer.java`

```java
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController { ... }
```

---

## Log Levels in Use

| Level | Usage | Example |
|-------|-------|---------|
| `log.error` | Exceptions, authentication failures, unexpected errors | `log.error(e.getMessage())` in `GlobalExceptionHandler` |
| `log.info` | Lifecycle events, request logging | `VertxWebSocketServer.start()`, `RequestLoggingFilter` |
| `log.debug` | Not currently used | — |

---

## Request Logging Filter

Reference: `filter/RequestLoggingFilter.java`

- Implements `jakarta.servlet.Filter`, `@Order(HIGHEST_PRECEDENCE)`.
- Wraps requests in `ContentCachingRequestWrapper`.
- Logs: `clientAddress:port - METHOD URI`.
- Manually extracts real IP from `X-Forwarded-For`, `Proxy-Client-IP`, and other proxy headers.
- Does **not** log response body or response status.

---

## WebSocket Logging

`VertxWebSocketServer` logs lifecycle events at INFO level:
- Server start: `"WebSocket服务器启动成功，端口: {}"`
- Server stop: `"WebSocket服务器已停止"`
- Connection register/unregister events.

---

## Conventions

1. Add `@Slf4j` to any class that needs logging — do not manually create `LoggerFactory.getLogger(...)`.
2. Use parameterized messages: `log.info("用户登录: {}", username)` — never string concatenation.
3. Log exceptions with the exception object as the last parameter: `log.error("处理消息失败", e)`.
4. Do not log sensitive information (passwords, tokens, personal data).

---

## Known Issues (Avoid Reproducing)

1. **`AuthController.login` logs `e.getMessage()` but does not log the full exception** — use `log.error("登录失败", e)` to preserve the stack trace.

2. **`RequestLoggingFilter` does not log response status or duration** — consider adding response status and elapsed time for request tracing.

3. **No correlation/request ID** is propagated — in a multi-instance setup with Redis Stream message processing, tracing a request across nodes is difficult. Consider adding a request ID to logs.

4. **`application.properties` has Chinese comments that appear as `?` (mojibake)** — the properties file encoding does not match the JVM default. Ensure properties files are saved as UTF-8 and `spring.config.encoding=UTF-8` is set if needed.
