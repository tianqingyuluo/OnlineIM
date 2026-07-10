# State Management

> Pinia store patterns: local state, global state, and persistence.

---

## Pinia Setup

- Pinia is initialized in both `main.ts` and `stores/index.ts` (the `stores/index.ts` version is unused — `main.ts` creates its own instance).
- `pinia-plugin-persistedstate` is registered globally.
- All stores use the **Options API** style (`defineStore('id', { state, actions, getters })`), not the setup function style.

Reference: `main.ts`
```ts
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'

const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)
app.use(pinia)
```

---

## Store Inventory

| Store | ID | Persisted | Responsibility |
|-------|----|-----------|----------------|
| `useUserStore` | `'user'` | Yes | Current user info + JWT token, login/logout/fetchUserData |
| `useListStore` | `'list'` | Yes | Conversations, friends, groups, user groups, blacklist, join requests — bulk initialization + IndexedDB sync |
| `useHistoryStore` | `'history'` | No | Message history (private/group), pending messages, IndexedDB range tracking, WebSocket message handling |
| `useWebSocketStore` | `'websocket'` | No | Thin wrapper over `websocketService` singleton |
| `useNotificationStore` | `'notificationStore'` | — | Notification badges (new friend, new group) |
| `useWidthStore` | `'width'` | — | Responsive width state |
| `useOtherStore` | `'otherStore'` | — | Miscellaneous state |

---

## State Conventions

- State is a function returning an object: `state: () => ({ ... })`.
- Array fields are typed with `as Type[]` inline: `conversations: [] as Conversation[]`.
- Default/empty objects use deep clone: `JSON.parse(JSON.stringify(DEFAULT_USER))`.

Reference: `stores/user.ts`
```ts
export const useUserStore = defineStore('user', {
  state: () => ({
    loggedInUser: JSON.parse(JSON.stringify(DEFAULT_USER)) as User,
    token: "",
  }),
  persist: true,
  actions: { ... },
});
```

---

## Actions

- Actions are async when they call services.
- Actions can call other stores: `const listStore = useListStore()`.
- Actions handle errors with try/catch + `console.error` + optional `toast`.

Reference: `stores/user.ts` — `clearUser()` resets state and calls `listStore` reset + `TokenService.clear()`.

Reference: `stores/list.ts` — `fetchUserData()` is the main initialization action:
1. Checks `hasInit` flag to avoid re-fetching.
2. Loads cached data from IndexedDB (`dbService.getAll`).
3. Fetches fresh data via `Promise.allSettled([friendGroupsService, conversationService, ...])`.
4. Syncs fresh data back to IndexedDB (`dbService.bulkPut`).

---

## Getters

- Getters are arrow functions receiving `state`.
- Parameterized getters return a function: `(state) => (arg) => ...`.

Reference: `stores/history.ts`
```ts
getters: {
  hasPendingMessages: (state) => (conversationId: string) => {
    return !!state.pendingMessages[conversationId]?.ranges?.length;
  },
  isMessagePending: (state) => (clientId: string) => {
    return state.pendingMessagesInfo.some(item => item.clientId === clientId);
  },
}
```

---

## Persistence

- `persist: true` on stores that should survive page reload (user, list).
- Uses `pinia-plugin-persistedstate` → data stored in `localStorage`.
- **Do not persist transient state** (WebSocket connection, message history being viewed) — use `persist: false` or omit.

---

## IndexedDB Integration

`useListStore` and `useHistoryStore` use IndexedDB (via `@/utils/indexedDB.ts`) as a local cache layer:

- **List store**: caches conversations, friends, groups, user groups, blacklist. On init, loads from IndexedDB first (fast display), then fetches from API and updates IndexedDB.
- **History store**: caches messages per conversation, tracks "pending ranges" (gaps in message history) to know when to fetch from server vs. local cache.

This dual-layer cache (Pinia + IndexedDB) enables offline-first message viewing.

---

## Known Issues (Avoid Reproducing)

1. **Duplicate Pinia instance**: `stores/index.ts` creates and exports a Pinia instance that is never used (not imported anywhere). Remove it to avoid confusion — `main.ts` is the single source of truth for Pinia setup.

2. **`useListStore.fetchUserData` is a 200+ line method** with deeply nested try/catch blocks. Break it into smaller actions (`loadFromCache`, `fetchFromApi`, `syncToIndexedDB`).

3. **`useHistoryStore` mixes concerns**: message state management, IndexedDB sync, WebSocket message handling, pending message tracking, and send-message logic are all in one 559-line store. Consider splitting into separate stores or composables.

4. **Empty catch blocks**: `useUserStore.clearUser` has `catch (error) { }` — at minimum log the error.

## Message Receipt Queue and Remote Cursor Invariants

- `useHistoryStore.readStates[conversationId]` always represents the **current logged-in user's** `delivered_seq`/`read_seq`; never overwrite it with a remote member's `RECEIPT` or `READ_RECEIPT` event.
- Store remote delivery/read cursors separately (`deliveredCursors` / `groupReadCursors`) and apply them only to messages authored by the current user when rendering sent-message state.
- `READ_RECEIPT` records use `dedupe_key = READ_RECEIPT:<conversation_id>` and are merged by numeric BigInt `read_seq`; an older pending cursor must not replace a newer one.
- Sending or flushing a receipt may delete an IndexedDB record only when its `id`, `dedupe_key`, and payload still match the record that was transmitted. This prevents an in-flight older receipt from deleting a newer cursor queued concurrently.
- `seq_id` comparisons must use the shared `@/utils/seq-id` BigInt helper; never use `Number`, subtraction, lexical comparison, or `localeCompare` for Snowflake IDs.

---

## Scenario: Reply-Aware Send State and Offline Queue

### 1. Scope / Trigger

- Trigger: changing `useHistoryStore` send/retry/ACK/error behavior, reply composer state, `MessageService.putMessage`, or WebSocket outbound queue normalization.
- Applies to private and group messages sent online, retried after timeout, or persisted while offline.

### 2. Signatures

```ts
sendMessage(
  conversationId: string,
  receiverId: string,
  messageType: string,
  content: string,
  isGroup: boolean,
  atUsers?: string[],
): string

MessageService.putMessage(
  targetId: string,
  messageType: string,
  content: string,
  replyToMessageId?: string,
  clientMessageId?: string,
): Promise<MessageResponse>
```

Outbound payloads use:

```ts
interface ReplyAwarePayload {
  client_message_id: string
  reply_to_message_id?: string
  conversation_id?: string
  target_id?: string
  group_id?: string
}
```

### 3. Contracts

- A local optimistic message stores the local `reply_to` preview, while every transport/retry queue stores the authoritative target ID as `reply_to_message_id`.
- Initial send, automatic timeout retry, manual retry, and IndexedDB outbound replay must preserve the same `client_message_id` and reply target.
- Queue conversation resolution accepts `conversation_id`, legacy `target_id`, and group `group_id`; group messages must never be dropped because only private keys were inspected.
- ACK cleanup is correlated by `client_message_id`. An old ACK may clear only the reply target used by that send, not a newer target selected afterward.
- `REPLY_TARGET_UNAVAILABLE` restores the failed body and reply context, but must not overwrite text the user typed after the failed send.
- Pinia action and getter names share one public namespace. Never define an action with the same name as a getter (for example `isMessagePending`), because the store can fail to mount at runtime.
- API and queue payload fields remain snake_case. TypeScript models mirror the wire format instead of adding per-call camelCase transforms.

### 4. Validation & Error Matrix

| Condition | Required behavior |
|---|---|
| No reply selected | Omit `reply_to_message_id`; send as a normal message |
| Reply selected for another conversation | Do not attach it to the outgoing message |
| Socket offline | Persist payload with the same client ID, reply ID, and private/group conversation key |
| ACK for an older send | Merge that message only; preserve a newer composer reply selection |
| `REPLY_TARGET_UNAVAILABLE` and editor is empty | Restore failed body and reply context |
| `REPLY_TARGET_UNAVAILABLE` and editor has new text | Preserve the new text; keep recovery data separately |
| Target recalled/unavailable | Set `preview_text` to `null` in Pinia and IndexedDB |

### 5. Good / Base / Bad Cases

- Good: a group reply goes offline, is stored with `group_id`, reconnects with the same client/reply IDs, and is persisted once.
- Base: a normal private message follows the existing send/ACK path without reply fields.
- Good: the user sends reply A, selects reply B, and then receives A's delayed ACK; reply B remains selected.
- Bad: reconstructing reply metadata from the current composer during retry, looking only at `conversation_id`, or defining matching action/getter names.

### 6. Tests Required

- Store unit: optimistic reply, ACK correlation, timeout retry, manual retry, and server rejection recovery.
- WebSocket service unit: offline queue preserves generated/stable client ID and `reply_to_message_id`; group payload resolves via `group_id`.
- IndexedDB unit/integration: recalled targets clear every direct cached preview.
- Component: close button, Escape, conversation switch, quick reply, context menu, two-line preview, and recalled/unavailable redaction.
- E2E: one context request for unloaded targets, unchanged scroll position on unavailable targets, and retry with unchanged IDs.

### 7. Wrong vs Correct

#### Wrong

```ts
// Drops group messages and rebuilds the reply from mutable composer state.
const conversationId = payload.conversation_id || payload.target_id
payload.reply_to_message_id = store.replyTarget?.reference.message_id
```

#### Correct

```ts
const conversationId = asString(
  payload.conversation_id ?? payload.target_id ?? payload.group_id,
)
const queuedPayload = {
  ...payload,
  client_message_id: pending.clientId,
  ...(pending.replyToMessageId
    ? { reply_to_message_id: pending.replyToMessageId }
    : {}),
}
```
