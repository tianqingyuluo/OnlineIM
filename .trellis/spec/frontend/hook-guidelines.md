# Service & Composable Guidelines

> API service patterns, WebSocket management, and composable conventions.

> **Note**: This project does not use Vue composables (custom `useXxx` hooks) for business logic. Instead, it uses **service modules** for API calls and **Pinia stores** for stateful logic. The only composable-style files are the UI primitive helpers (e.g. `components/ui/form/useFormField.ts`).

---

## Service Layer Pattern

Services are plain modules that wrap API calls. Each service is one file per resource, exporting a singleton object or class.

### HTTP Services

All HTTP services import the shared axios instance from `services/api.service.ts`.

Reference: `services/api.service.ts`
```ts
import axios from 'axios';
import { API_BASE_URL } from '../../shared/config.ts';

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
});

// Request interceptor: inject Bearer token from user store
api.interceptors.request.use((config) => {
  const token = useUserStore().token;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Response interceptor: normalize errors
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const { status, data } = error.response;
    return Promise.reject({ code: data?.code || `HTTP_${status}`, message: data?.message, status, ...data });
  }
);

export const http = {
  get: <T>(url: string, config?: any) => api.get<T>(url, config),
  post: <T>(url: string, data?: any, config?: any) => api.post<T>(url, data, config),
  // ...
};
export default api;
```

### Resource Service Pattern

Each resource service exports an object with async methods returning typed responses.

Reference: `services/message.service.ts`
```ts
import api from './api.service';
import type { MessageResponse } from "@/type/message.ts";

export const MessageService = {
  async getMessageHistory(conversation_id: string, seq_id?: string): Promise<{messages: MessageResponse[], has_more_before: boolean}> {
    const response = await api.get(`/messages/${conversation_id}`, { params: { seq_id } });
    return response.data;
  },
};
```

Reference: `services/auth.service.ts`, `services/friends.service.ts`, `services/group.service.ts`

### Service Conventions

- Import the axios instance as `import api from './api.service'`.
- Return `response.data` (unwrap the axios response).
- Use query params via `{ params: { ... } }` config.
- Type all return values with interfaces from `@/type/`.
- Field names in request/response use `snake_case` to match the backend API.

## Scenario: Authenticated CORS-Like HTTP Failures

### 1. Scope / Trigger

- Trigger: a service call with `Authorization` fails in the browser as a CORS/network error, or a backend response envelope is consumed by a component/store.
- This is a cross-layer contract: browser preflight -> Spring CORS/security -> Axios interceptor -> resource service -> store/component.

### 2. Signatures

- Axios interceptor failure output:
  ```ts
  type NormalizedApiError = {
    code: string;
    message: string;
    details: Record<string, unknown>;
    status: number;
  };
  ```
- Resource service methods return the component-ready payload, not the raw transport envelope:
  ```ts
  async getReceivedFriendRequests(): Promise<FriendRequest[]>
  async handleFriendRequest(id: string, action: 'accept' | 'reject' | 'refuse'): Promise<{ friend_id?: string }>
  ```

### 3. Contracts

- If `error.response` is absent, the response interceptor must reject immediately with `status: 0` and must not read `error.response.status`.
- If the backend returns an envelope such as `{ requests, total }`, the service unwraps it before updating Pinia state.
- Components must never build URL path segments from untyped response objects. Extract the exact field first, e.g. `response.friend_id`.
- Store fields typed as arrays must stay arrays across empty, 404, and successful responses.

### 4. Validation & Error Matrix

- `!error.response` -> reject `{ code: 'NETWORK_ERROR', status: 0 }`.
- `404` for an optional list endpoint -> service may return `[]` only when "empty list" is valid product behavior.
- Missing required response field, e.g. no `friend_id` after accepting a request -> caller throws a local error and does not call the follow-up API.
- HTTP 4xx/5xx with `error.response` -> preserve backend `code`, `message`, `status`.

### 5. Good/Base/Bad Cases

- Good: `const { friend_id } = await friendsService.handleFriendRequest(id, 'accept')`, then call `/friends/${friend_id}/group`.
- Base: `getReceivedFriendRequests()` returns `[]` for "no pending requests".
- Bad: assigning `{ requests, total }` to `FriendRequest[]` or passing the whole response object into a URL path, producing `[object Object]`.

### 6. Tests Required

- Service test: mocked 404 list response resolves to `[]`.
- Service test: mocked network/CORS failure rejects with `status: 0` and does not throw `Cannot read property response`.
- Component/store test: accepting a friend request calls `setFriendGroup` with the response `friend_id` string, not the response object.

### 7. Wrong vs Correct

#### Wrong

```ts
const friendId = await friendsService.handleFriendRequest(requestId, 'accept');
await api.put(`/friends/${friendId}/group`, body); // friendId may be an object
```

#### Correct

```ts
const response = await friendsService.handleFriendRequest(requestId, 'accept');
if (!response.friend_id) throw new Error('missing friend_id');
await api.put(`/friends/${response.friend_id}/group`, body);
```

---

## Token Service (Singleton)

`services/token.service.ts` implements a singleton auto-refresh pattern:

Reference: `services/token.service.ts`
```ts
export class TokenService {
  private static instance: TokenService | null = null;

  public static init(token: string): TokenService {
    if (!this.instance) {
      this.instance = new TokenService(token);
      this.instance.startAutoRefresh();
    }
    return this.instance;
  }

  public static clear(): void {
    this.instance?.stopAutoRefresh();
    this.instance = null;
  }
}
```

- `TokenService.init(token)` is called after login to start auto-refresh.
- `TokenService.clear()` is called on logout to stop the timer.
- Auto-refresh schedules `setTimeout` based on `expires_in - 30` seconds.

---

## WebSocket Service

`services/websocket.service.ts` is a singleton class managing the WebSocket connection.

Reference: `services/websocket.service.ts`
```ts
class WebSocketService {
  private ws: WebSocket | null = null;
  public isConnected = ref(false);

  connect(): void {
    const token = useUserStore().token;
    const url = `${WS_API_URL}?token=Bearer ${token}`;
    this.ws = new WebSocket(url);
    this.ws.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (message.type === 'PRIVATE_MESSAGE_RESPONSE') {
        useHistoryStore().handleWebSocketMessage(message.message);
      }
      if (message.type === 'GROUP_MESSAGE_RESPONSE') {
        useHistoryStore().handleGroupWebSocketMessage(message.message);
      }
    };
  }

  sendMessage(payload: { type: string, message: any }): void {
    this.ws?.send(JSON.stringify(payload));
  }
}
export const websocketService = new WebSocketService();
```

### WebSocket Message Protocol

Matches the backend protocol: `{ type: "<TYPE>", message: {<payload>} }`

| Direction | Type | Handler |
|-----------|------|---------|
| Outbound | `PRIVATE_MESSAGE_REQUEST` | Backend `PrivateMessageSender` |
| Outbound | `GROUP_MESSAGE_REQUEST` | Backend `GroupMessageSender` |
| Inbound | `PRIVATE_MESSAGE_RESPONSE` | `historyStore.handleWebSocketMessage` |
| Inbound | `GROUP_MESSAGE_RESPONSE` | `historyStore.handleGroupWebSocketMessage` |

### WebSocket Store Wrapper

`stores/websocketStore.ts` is a thin Pinia wrapper around `websocketService` — it delegates `connect()`, `disconnect()`, `sendMessage()` to the service and exposes `isConnected` as state.

---

## Known Issues (Avoid Reproducing)

1. **Broken import paths**: `api.service.ts` and `websocket.service.ts` import from `'../../shared/config.ts'`, and `proxy-server.js` imports from `'./shared/config.js'`. The `shared/` directory **does not exist** — the actual config is at `src/config.ts`. Additionally, `WS_API_URL` is imported in `websocket.service.ts` but is **not defined** in `src/config.ts` (only `API_BASE_URL` is). These imports must be fixed to use `@/config`.

2. **Response interceptor error handling is commented out**: The `switch(status)` block in `api.service.ts` has all cases (401, 403, 404, 429, 500) commented out. Only network errors (`!error.response`) produce a toast. HTTP error status codes are silently rejected without user feedback. Restore the error handling for production use.

3. **`message.service.ts` `putMessage`** has URL `/api/v1/messages/group` which double-prefixes `/api/v1` (the axios `baseURL` already includes `/api/v1`). The URL should be `/messages/group`.

4. **`auth.service.ts` `updateToken`** logs "登出失败" (logout failed) in the error message — copy-paste error, should say "刷新Token失败" (token refresh failed).
