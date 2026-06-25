# Type Safety

> TypeScript patterns, type definitions, and API model alignment.

---

## TypeScript Configuration

- TypeScript 5.7.2, strict mode via `@vue/tsconfig`.
- `tsconfig.json` references `tsconfig.app.json` (app code) and `tsconfig.node.json` (Vite config).
- Build runs `vue-tsc -b && vite build` — type errors will fail the build.

---

## Type Definitions (`src/type/`)

All API model types live in `src/type/` as exported interfaces. Field names use **`snake_case`** to match the backend JSON response format (the backend uses `@JsonProperty` for snake_case serialization).

Reference: `type/User.ts`
```ts
export interface User {
    user_id: string;
    username: string;
    nickname: string;
    avatar_url: string;
    region?: string;
    gender?: string;
    email?: string;
    phone?: string;
    signature?: string;
    created_at?: string;
}

export interface TokenResponse {
    access_token: string;
    expires_in: number;
}
```

Reference: `type/message.ts`
```ts
export interface MessageResponse {
    message_id: string;
    conversation_id: string;
    sender_info: {
        user_id: string;
        nickname: string;
        avatar_url?: string;
    };
    message_type: string;
    seq_id: string;
    content: any;
    status: number;
    timestamp: string;
    is_recalled: boolean;
    client_message_id: string;
}
```

### Type Files

| File | Types |
|------|-------|
| `User.ts` | `User`, `UserSearchResult`, `UserSearchResponse`, `TokenResponse` |
| `message.ts` | `MessageResponse`, `privateMessageResponse`, `MessageHistoryResponse` |
| `Conversation.ts` | `Conversation` |
| `group.ts` | `GroupResponse`, `GroupJoinRequestResponse` |
| `groupAnnouncement.ts` | Group announcement types |
| `groupsetting.ts` | Group settings types |
| `userGroup.ts` | `UserGroupInfo` |
| `Friends.ts` | `Friend`, `FriendInFriendGroup`, `FriendRequest` |

---

## Typing Conventions

1. **API field names are `snake_case`** in interfaces to match backend `@JsonProperty` output. Do not convert to camelCase on the frontend — keep the wire format.

2. **Optional fields use `?`**: `region?: string`, `avatar_url?: string`.

3. **Use `interface` for object shapes**, `type` for unions/aliases.

4. **Template literal types** for ID prefixes: `user_id: \`usr_${string}\`` (see `UserSearchResult`).

5. **Avoid `any`** — the codebase has some `any` usage (`content: any` in `MessageResponse`, `any[]` in history store) that should be replaced with proper types.

---

## Zod Schema Integration

Zod is used for form validation (via `@vee-validate/zod`). Schemas are defined inline in components and can infer types:

Reference: `views/Login.vue`
```ts
import * as z from 'zod'
import { toTypedSchema } from '@vee-validate/zod'

const formSchema = toTypedSchema(z.object({
  username: z.string().min(4).max(16),
  password: z.string().min(6).max(20),
}))
```

**Convention**: Zod schemas are currently defined per-component. For shared validation rules (e.g. `LoginService.validationRules`), co-locate the rules with the service and derive the Zod schema there.

---

## API Response Type

`services/api.service.ts` defines a generic `ApiResponse` type, though it is not consistently used by all services:

```ts
export type ApiResponse<T = any> = {
  data?: T;
  error?: {
    code: string;
    message: string;
    details?: Record<string, string[]>;
  };
};
```

---

## Known Issues (Avoid Reproducing)

1. **`content: any` in `MessageResponse`** — message content varies by `message_type` (text string, image URL, file metadata). Define a discriminated union or `Record<string, unknown>` instead of `any`.

2. **`privateMessageResponse` uses PascalCase name** (`privateMessageResponse`) — should be `PrivateMessageResponse` per TypeScript convention. The interface also has a typo: `creat_at` instead of `created_at`.

3. **Inconsistent type imports**: Some files use `import {type User}` (inline type modifier), others use `import type {User}`. Pick one style — `import type {}` is preferred for type-only imports.

4. **`UserSearchResult.user_id` uses template literal type** `\`usr_${string}\`` but `User.user_id` is plain `string`. Align the typing — either both use the literal type or neither.

5. **`MessageService.getMessageHistory` and `getPrivateHistory` have different return types** (one has `has_more_after`, the other doesn't) despite hitting the same endpoint. Unify the return type.
