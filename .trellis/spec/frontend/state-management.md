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
