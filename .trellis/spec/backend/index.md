# Backend Development Guidelines

> Coding conventions for `onlineIM-server` — a Spring Boot 3.4.4 / Java 21 IM backend.

---

## Tech Stack

- **Framework**: Spring Boot 3.4.4, Java 21
- **HTTP**: Spring MVC (port 8080)
- **WebSocket**: Vert.x 4.5.11 (separate HTTP server, port 8081, path `/api/v1/chat`)
- **MySQL**: MyBatis 3.0.3 (annotation + XML mappers)
- **MongoDB**: Spring Data MongoDB
- **Redis**: Spring Data Redis (Lettuce) — caching, token blacklist, WebSocket cross-node messaging via Redis Stream
- **Auth**: JWT (jjwt 0.11.5), Spring Security
- **Storage**: Multi-OSS adapter (MinIO, AWS S3, Aliyun OSS, Tencent COS)
- **Utilities**: Hutool, Lombok

---

## Guidelines Index

| Guide | Description |
|-------|-------------|
| [Directory Structure](./directory-structure.md) | Package layout, layering, calling conventions, naming |
| [Database Guidelines](./database-guidelines.md) | MySQL/MyBatis + MongoDB dual-store patterns, entity vs document, known issues |
| [Error Handling](./error-handling.md) | GlobalExceptionHandler, ErrorResponse, exception conventions, known issues |
| [Logging Guidelines](./logging-guidelines.md) | SLF4J/@Slf4j, request logging, log levels |
| [Quality Guidelines](./quality-guidelines.md) | DI, transactions, thread safety, security, forbidden patterns, build commands |

---

## Key Architecture Decisions

1. **Dual persistence**: MySQL for relational business data, MongoDB for high-volume message/conversation data. See [Database Guidelines](./database-guidelines.md).
2. **Separate WebSocket server**: Vert.x runs on port 8081 independent of the Spring HTTP server (8080), connected via Spring's event bus and Redis Stream for cross-node delivery.
3. **OSS adapter pattern**: Static factory (`OSSAdapterFactory`) creates one of four adapters based on `oss.type` config, wired into Spring via `OSSConfig.ossAdapter()` `@Bean`.
4. **MyBatis dual mapper**: Entity mappers (operate on POJOs) + response mappers (return DTOs from complex joins).

---

## Quick Reference

- API base path: `/api/v1/<resource>`
- WebSocket path: `/api/v1/chat?token=Bearer%20<jwt>`
- WebSocket message protocol: `{ "type": "<TYPE>", "message": {<payload>} }`
- Redis Stream key: `im:message:stream`
- ID format: `<prefix>_<snowflake>` (e.g. `usr_`, `grp_`, `msg_`, `conv_`)

---

**Language**: Documentation is written in English. Code comments in the project are in Chinese.
