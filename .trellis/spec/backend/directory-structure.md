# Directory Structure

> How backend code is organized in `onlineIM-server`.

---

## Overview

Single Maven module, standard Spring Boot layered architecture. All Java code lives under the package root `icu.tianqingyuluo.onlineim` and is split by responsibility into sub-packages. The project uses **two persistence stores** (MySQL via MyBatis + MongoDB via Spring Data) and a **separate Vert.x WebSocket server** alongside the Spring HTTP server.

---

## Directory Layout

```
onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/
├── OnlineIMApplication.java          # @SpringBootApplication, @MapperScan("...mapper"), @EnableAsync
├── config/                           # Spring @Configuration classes (6)
│   ├── RedisConfig.java              #   RedisTemplate, CacheManager, Stream listener container
│   ├── OSSConfig.java                #   @ConfigurationProperties("oss") + @Bean ossAdapter()
│   ├── SecurityConfig.java           #   SecurityFilterChain, CORS, BCrypt, AuthenticationManager
│   ├── WebSocketConfig.java          #   Empty placeholder (components self-register via @Component)
│   ├── AsyncConfig.java              #   @EnableAsync, VirtualThreadTaskExecutor
│   └── SchedulingConfig.java         #   @EnableScheduling, ThreadPoolTaskScheduler
├── controller/                       # @RestController, @RequestMapping("/api/v1/<resource>") (11)
├── filter/                           # Servlet filters
│   ├── JwtAuthenticationFilter.java  #   OncePerRequestFilter, puts UserDetails into SecurityContext
│   └── RequestLoggingFilter.java     #   @Order(HIGHEST_PRECEDENCE), logs request line
├── exception/                        # GlobalExceptionHandler + custom exceptions
├── pojo/
│   ├── entity/                       # MySQL POJOs (MyBatis, no JPA annotations) (10)
│   ├── document/                     # MongoDB @Document classes (5)
│   └── dto/                          # request/, response/, request/websocket/
├── mapper/                           # MyBatis @Mapper interfaces (entity mappers + response mappers)
├── repository/                       # Spring Data MongoDB @Repository interfaces (5)
├── service/                          # Service interfaces (12)
│   └── impl/                         # Service implementations (10)
├── storage/                          # OSS adapter pattern
│   ├── OSSAdapter.java               #   Interface
│   ├── OSSAdapterFactory.java        #   Static factory
│   └── impl/                         #   MinIO, S3, Aliyun, Tencent adapters
├── util/                             # JwtUtil, ErrorCodeUtil, RealIPUtil, enumeration/
├── scheduler/                        # RedisCleanupScheduler
└── websocket/                        # Vert.x WebSocket subsystem
    ├── server/                       #   VertxWebSocketServer (lifecycle, @PostConstruct/@PreDestroy)
    ├── handler/                      #   WebSocketAuthenticator, WebSocketMessageRouter
    ├── processor/                    #   WebSocketMessageProcessor (Spring @EventListener)
    ├── session/                      #   WebSocketSession wrapper
    ├── registry/                     #   LocalSessionRegistry (ConcurrentHashMap-based)
    ├── event/                        #   RedisStreamEvent, WebSocketMessageEvent
    └── listener/                     #   RedisEventListener + handler/{sender,receiver}/
```

Resources live in `src/main/resources/`:

```
resources/
├── application.properties            # Main config (HTTP 8080, WS 8081, MySQL, Redis, MyBatis)
├── application-oss-example.properties # OSS config template for 4 providers
├── db.properties                     # Externalized DB credentials (imported via spring.config.import)
├── onlineIM.sql                      # MySQL schema (9 tables, InnoDB, utf8mb4_unicode_ci)
└── mapper/                           # MyBatis XML mappers (*.xml, namespace = interface FQN)
```

---

## Layer Calling Conventions

### HTTP request flow

```
RequestLoggingFilter → JwtAuthenticationFilter → SecurityFilterChain
  → Controller → Service(impl) → Mapper (MySQL) or Repository (MongoDB)
```

- Controllers should be thin: parse request, delegate to service, return response.
- Services contain business logic and transaction boundaries (`@Transactional` on write methods).
- Mappers/Repositories are the only layers that touch the database.

### WebSocket message flow

**Inbound (client → server):**
```
VertxWebSocketServer → WebSocketAuthenticator(JWT) → LocalSessionRegistry(register)
  → WebSocketMessageRouter(parse type) → MessageSenderHandler(persist to MongoDB + publish Redis Stream)
```

**Cross-node delivery (Redis Stream):**
```
MessageSenderHandler → RedisStreamService.publish(im:message:stream)
  → RedisEventListener(onMessage, filters by receiver locality)
  → Spring event bus → WebSocketMessageProcessor(@EventListener)
  → MessageReceiverHandler → LocalSessionRegistry.getByUserId → WebSocketSession.sendMessage
```

### Storage flow

```
Service → FileStorageService → OSSAdapter (created by OSSAdapterFactory) → concrete OSS provider
```

---

## Module Organization

- **One package per layer**, not one package per feature. All controllers are in `controller/`, all services in `service/` + `service/impl/`.
- New features add files to the appropriate layer package rather than creating a feature-scoped package.
- The `websocket/` sub-package is self-contained with its own sub-layers (`server/`, `handler/`, `processor/`, `session/`, `registry/`, `event/`, `listener/`).

---

## Naming Conventions

| Artifact | Convention | Example |
|----------|-----------|---------|
| Package | lowercase | `icu.tianqingyuluo.onlineim.controller` |
| Controller | `<Resource>Controller` | `GroupController`, `MessageController` |
| Service interface | `<Resource>Service` | `GroupService` |
| Service impl | `<Resource>ServiceImpl` | `GroupServiceImpl` |
| Entity (MySQL) | singular noun | `Group`, `GroupMember` |
| Document (MongoDB) | singular noun | `PrivateMessage`, `Conversation` |
| MyBatis entity mapper | `<Entity>Mapper` | `UserMapper`, `GroupMapper` |
| MyBatis response mapper | `<Entity>ResponseMapper` | `UserResponseMapper`, `GroupResponseMapper` |
| MongoDB repository | `<Document>Repository` | `PrivateMessageRepository` |
| Request DTO | `<Action><Resource>Request` | `GroupCreateRequest`, `UserRegisterRequest` |
| Response DTO | `<Resource>Response` | `UserResponse`, `GroupResponse` |
| Config class | `<Tech>Config` | `RedisConfig`, `SecurityConfig` |
| ID prefix | `<3-char>_<snowflake>` | `usr_`, `grp_`, `mem_`, `conv_`, `msg_`, `req_`, `jrq_`, `rel_`, `fgrp_`, `set_`, `ann_` |

---

## Examples

- Well-organized controller: `controller/AuthController.java` — clear `@RequestMapping("/api/v1/auth")`, POST endpoints for login/register/refresh/logout.
- Service interface + impl: `service/UserService.java` + `service/impl/UserServiceImpl.java`.
- MyBatis entity mapper with mixed annotation + XML: `mapper/UserMapper.java` + `resources/mapper/UserMapper.xml`.
- MongoDB repository with derived query + `@Query` + `@Aggregation`: `repository/ConversationRepository.java`.
- WebSocket handler registration pattern: `websocket/listener/handler/MessageTypeSenderRegistry.java`.
