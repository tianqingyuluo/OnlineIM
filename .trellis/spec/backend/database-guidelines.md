# Database Guidelines

> Dual-store persistence: MySQL (MyBatis) for relational data, MongoDB (Spring Data) for message/conversation data.

---

## Two-Store Strategy

| Store | Technology | Used For | Layer |
|-------|-----------|----------|-------|
| MySQL | MyBatis (annotation + XML) | Users, groups, members, friends, friend requests, group settings, announcements, join requests | `mapper/` + `pojo/entity/` |
| MongoDB | Spring Data MongoDB | Messages (private/group), conversations, user timelines, recall logs | `repository/` + `pojo/document/` |

**Principle**: Relational, strong-consistency, transactional business data → MySQL. High-volume, write-heavy, time-series, conversation-aggregated data → MongoDB.

---

## MySQL / MyBatis

### Entity POJOs (`pojo/entity/`)

- Pure POJOs with **no MyBatis/JPA annotations** — mapping is done via XML `resultMap` or `@Select` column aliases.
- Always use Lombok four-annotation set: `@Data @Builder @NoArgsConstructor @AllArgsConstructor`.
- Time fields use `java.time.LocalDateTime`.
- ID fields are `String` with a documented prefix (e.g. `usr_`, `grp_`).
- Status/role/type fields use `Integer` with inline comments documenting the encoding.

Reference: `pojo/entity/Group.java`
```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Group {
    private String id;             // 群组ID，格式为：grp_+UUID
    private String ownerId;        // usr_+UUID
    private Integer joinType;      // 0/1/2 用整数编码枚举
    private Integer status;        // 0-已解散，1-正常
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### Two Mapper Types

**Entity Mapper** — operates on entity POJOs, mixes `@Select/@Insert/@Update/@Delete` annotations with XML for dynamic queries.

Reference: `mapper/UserMapper.java`
```java
@Mapper
public interface UserMapper {
    @Select("SELECT id, username, ... FROM users WHERE id = #{id}")
    User getById(String id);

    // No annotation → implemented in XML
    List<User> getList(@Param("keyword") String keyword, @Param("offset") Integer offset, @Param("limit") Integer limit);
}
```

**Response Mapper** — returns DTOs directly, all SQL in XML (complex joins, window functions).

Reference: `mapper/UserResponseMapper.java`, `mapper/GroupResponseMapper.xml`

### XML Mapper Conventions

- Location: `src/main/resources/mapper/*.xml`, namespace = interface FQN.
- Configured via `mybatis.mapper-locations=classpath*:mapper/*.xml`.
- Use `resultMap` for any query returning an entity with `snake_case` → `camelCase` mapping.
- Dynamic SQL with `<where>`, `<if>`, `<set>`, `<foreach>`.
- Pagination via `LIMIT #{limit} OFFSET #{offset}`.

Reference: `resources/mapper/UserMapper.xml`, `resources/mapper/GroupMapper.xml`

### ID Generation

- IDs are generated in **Java service layer** using `cn.hutool.core.util.IdUtil.getSnowflakeNextIdStr()` with a type prefix.
- Example: `"grp_" + IdUtil.getSnowflakeNextIdStr()` in `GroupServiceImpl.createGroup`.

### Schema (`resources/onlineIM.sql`)

- 9 tables, InnoDB, `utf8mb4_unicode_ci`.
- Column names `snake_case`, Java properties `camelCase`.
- ID columns `VARCHAR(143)`.
- Status/type columns `TINYINT UNSIGNED`.
- Time columns `DATETIME DEFAULT CURRENT_TIMESTAMP [ON UPDATE CURRENT_TIMESTAMP]`.
- Indexes: `uk_*` unique, `idx_*` normal, composite indexes prefixed with business dimension.
- **No foreign key constraints** — referential integrity is maintained at the application layer.

---

## MongoDB / Spring Data

### Document POJOs (`pojo/document/`)

- Use `@Document(collection = "xxx")` + `@Id` (Spring Data).
- Lombok four-annotation set.
- Time fields use `java.util.Date` (differs from entity `LocalDateTime`).
- `ext` field of type `Map<String, Object>` for extensibility.

Reference: `pojo/document/PrivateMessage.java`
```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Document(collection = "private_messages")
public class PrivateMessage {
    @Id
    private String id;             // msg_+UUID
    private String conversationId;
    private String messageType;    // text, image, voice, file...
    private String seqId;
    private Date timestamp;
    private Map<String, Object> ext;
}
```

### Repository Conventions

- `@Repository` interface extending `MongoRepository<Document, String>`.
- Simple queries: derived method names (`findByXxxOrderByYyyDesc`).
- Complex conditions: `@Query` with JSON-style query string.
- Aggregations: `@Aggregation(pipeline = { ... })`.
- Pagination via `Pageable`/`Page`, sorting via `Sort`.

Reference: `repository/ConversationRepository.java`
```java
@Query("{ 'status': 1, '$or': [ { 'userId': ?0 }, { 'targetId': ?0 } ] }")
List<Conversation> findUserConversations(String userId, Sort sort);

@Aggregation(pipeline = {
    "{ $match: { 'userId': ?0, 'status': 1 } }",
    "{ $group: { _id: null, total: { $sum: '$unreadCount' } } }"
})
Integer findTotalUnreadCount(String userId);
```

---

## Known Issues (Avoid Reproducing)

These inconsistencies exist in the current codebase and should not be replicated in new code:

1. **seqId type mismatch**: `PrivateMessage.seqId` / `GroupMessage.seqId` / `RecallLog.seqId` are `String`, but `UserTimeline.seqId` is `Long`. The derived query `findByConversationIdAndSeqIdGreaterThanOrderBySeqIdAsc(String, Long)` passes `Long` against a `String` field — comparison behaves incorrectly. New documents should use a consistent `seqId` type.

2. **Time type split**: entities use `LocalDateTime`, documents use `Date`. When adding new entities/documents, follow the existing convention for that store type.

3. **`@JsonProperty` inconsistency**: `GroupMessage.clientMessageId` has `@JsonProperty("client_message_id")` but `PrivateMessage.clientMessageId` does not. Always add `@JsonProperty` for snake_case JSON fields in response DTOs.

4. **Status fields as magic numbers**: All status/role/type fields are `Integer`/`String` with inline comments. There is only one enum (`util/enumeration/FriendshipStatusEnum.java`) and it is unused by entities. Prefer enums for new status fields.

5. **`Group.muteType` field has no corresponding `groups` table column** — it lives in `group_settings`. The `GroupMapper.xml` `GroupResultMap` maps `muteType → mute_type`, which will fail on `SELECT g.*` from `groups`.

6. **SQL schema file has duplicate content** (`resources/onlineIM.sql`) — a commented-out old version followed by the active version. Only the active version (line 143+) is authoritative.

7. **`FriendResponseMapper` is missing `@Mapper` annotation** — works only because `@MapperScan` on the application class covers it. Always add `@Mapper` to new mapper interfaces for consistency.

8. **ID generation in XML via `${}` OGNL**: `GroupMapper.xml` uses `${@cn.hutool.core.util.IdUtil@getSnowflakeNextIdStr()}` inside SQL. Generate IDs in the Java service layer and pass them as parameters instead.
