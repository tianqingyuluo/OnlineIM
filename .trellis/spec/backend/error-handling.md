# Error Handling

> How errors are represented, caught, and surfaced in the backend.

---

## Overview

The project has a `@RestControllerAdvice` global exception handler, two custom exceptions, and an `ErrorResponse` DTO. However, the error-handling system is **fragmented** — there are multiple parallel error response formats and the global handler does not cover all custom exceptions.

---

## Global Exception Handler

Reference: `exception/GlobalExceptionHandler.java`

```java
@Slf4j @RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException e) {
        return ResponseEntity.status(401).body(new ErrorResponse("401", "用户名或密码错误"));
    }

    @ExceptionHandler(UsernameConflictException.class)
    public ResponseEntity<ErrorResponse> handleUsernameConflict(UsernameConflictException e) {
        return ResponseEntity.status(409).body(new ErrorResponse("409", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception e) {
        return ResponseEntity.status(500).body(new ErrorResponse("500", "服务器内部错误"));
    }
}
```

- Handlers return `ResponseEntity<ErrorResponse>` with HTTP status + `ErrorResponse(code, message)`.
- The catch-all `Exception.class` handler returns 500 and swallows the original message.

---

## Error Response Formats

There are **three** error response formats in use — this is a known inconsistency:

### 1. ErrorResponse object (via GlobalExceptionHandler)

Reference: `pojo/dto/response/ErrorResponse.java`
```java
@Data @NoArgsConstructor @AllArgsConstructor
public class ErrorResponse {
    private String code;      // HTTP status code as string ("401", "409", "500")
    private String message;
}
```

### 2. ErrorCodeUtil Map (used directly in Controllers)

Reference: `util/ErrorCodeUtil.java`
```java
public static Map<String, String> getErrorOutput(String errorCode, String errorMessage) {
    Map<String, String> map = new HashMap<>();
    map.put("code", errorCode);
    map.put("message", errorMessage);
    return map;
}
```

Usage in `controller/MessageController.java`:
```java
return ResponseEntity.status(401).body(ErrorCodeUtil.getErrorOutput("401", "权限不足"));
```

### 3. WebSocket error record (in WebSocketMessageRouter)

A local `record ErrorResponse(String type, String message)` defined inside the router class.

---

## Custom Exceptions

| Exception | Package | `@ResponseStatus` | Handled by GlobalExceptionHandler? |
|-----------|---------|-------------------|------------------------------------|
| `ForbiddenException` | `exception/` | No | **No** — falls through to `Exception.class` → 500 |
| `UsernameConflictException` | `exception/` | `@ResponseStatus(HttpStatus.CONFLICT)` | Yes → 409 |

Reference: `exception/ForbiddenException.java`, `exception/UsernameConflictException.java`

---

## Error Code Convention

- Error codes are **HTTP status codes as strings** (`"400"`, `"401"`, `"403"`, `"404"`, `"409"`, `"500"`).
- There is **no business error code enum or constants class** — codes are hardcoded strings scattered across controllers.
- No fine-grained business error codes (e.g. "USER_NOT_FOUND" vs "USER_DISABLED").

---

## Exception Throwing in Services

Services throw a mix of standard and custom exceptions:

| Situation | Exception thrown | Example |
|-----------|-----------------|---------|
| Invalid argument | `IllegalArgumentException` | `GroupServiceImpl` |
| Permission denied | `ForbiddenException` (custom) | `GroupServiceImpl.requestToJoinGroup` |
| Illegal state | `IllegalStateException` | various |
| Username conflict | `UsernameConflictException` (custom) | `UserServiceImpl` |
| Catch-all | `RuntimeException("...失败", e)` | `GroupServiceImpl.requestToJoinGroup` |

---

## Authentication Errors

- `JwtAuthenticationFilter` checks a Redis-backed token blacklist (`jwtService.isBlockedToken`) and returns `401` via `response.sendError(401, "token已经失效")`.
- `BadCredentialsException` from Spring Security is caught by the global handler → 401.
- Controllers also manually check `jwtUtil.getUserIDFromToken(token)` and return 401 if null — this duplicates the filter's authentication.

---

## Known Issues (Avoid Reproducing)

1. **`ForbiddenException` is not handled by `GlobalExceptionHandler`** — it falls through to the catch-all `Exception.class` handler and returns 500 instead of 403. Controllers that manually catch `ForbiddenException` and convert to 403 work, but any uncaught `ForbiddenException` loses its HTTP semantics. Add a `@ExceptionHandler(ForbiddenException.class)` handler returning 403.

2. **Two error response formats coexist** (`ErrorResponse` object vs `ErrorCodeUtil` Map). New code should use `ErrorResponse` via the global handler. Do not call `ErrorCodeUtil.getErrorOutput` directly in controllers — throw an exception instead.

3. **`ForbiddenException` lacks `@ResponseStatus`** while `UsernameConflictException` has it. Be consistent — either rely on `@ResponseStatus` or on `@ExceptionHandler`, not both.

4. **`AuthController.login` catches `Exception` but does not return** — execution continues to token generation after a login failure. Always `return` or `throw` inside catch blocks for authentication failures.

5. **`AuthController.register` echoes `password` in the response body** — never include plaintext passwords in any response.

6. **Controllers manually parse JWT tokens** with `@RequestHeader("Authorization")` instead of using `SecurityContextHolder` / `@AuthenticationPrincipal`. The `JwtAuthenticationFilter` already authenticated the user and put `UserDetails` (specifically `UserDetail` which implements `UserIDProvider`) into the security context. New controllers should use `@AuthenticationPrincipal` to get the authenticated user.
