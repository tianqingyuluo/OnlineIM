# Frontend Development Guidelines

> Coding conventions for `OnlineIM-Vue` — a Vue 3 + Vite IM client.

---

## Tech Stack

- **Framework**: Vue 3.5 (Composition API, `<script setup lang="ts">`)
- **Build**: Vite 6, TypeScript 5.7
- **State**: Pinia 3 + pinia-plugin-persistedstate
- **Routing**: Vue Router 4
- **Styling**: Tailwind CSS 4 (via `@tailwindcss/vite`), `reka-ui` (headless UI), `lucide-vue-next` (icons)
- **Forms**: VeeValidate 4 + `@vee-validate/zod` + Zod
- **HTTP**: Axios
- **Realtime**: Native WebSocket
- **Local cache**: IndexedDB via `idb`
- **Notifications**: vue-sonner (toast)
- **Other**: `@vueuse/core`, `ua-parser-js`, `class-variance-authority`, `clsx`, `tailwind-merge`

---

## Guidelines Index

| Guide | Description |
|-------|-------------|
| [Directory Structure](./directory-structure.md) | `src/` layout, layer responsibilities, naming conventions, path alias |
| [Component Guidelines](./component-guidelines.md) | `<script setup>` patterns, UI primitives, forms, styling, toasts |
| [Service & Composable Guidelines](./hook-guidelines.md) | API service pattern, WebSocket service, token refresh singleton |
| [State Management](./state-management.md) | Pinia stores, persistence, IndexedDB cache layer |
| [Quality Guidelines](./quality-guidelines.md) | Build commands, imports, error handling, routing, forbidden patterns |
| [Type Safety](./type-safety.md) | Type definitions, snake_case API fields, Zod schemas |
| [Device display name](../backend/device-display-name.md) | Login `device_name` and the logged-in device list label |

---

## Key Architecture Decisions

1. **Options API Pinia stores** — all stores use `defineStore('id', { state, actions, getters })`, not the setup function style.
2. **Dual-layer cache** — Pinia (in-memory + localStorage persistence) + IndexedDB (offline message/conversation cache). `useListStore` loads from IndexedDB first for instant display, then syncs from API.
3. **Singleton services** — `websocketService` and `TokenService` are singletons exported from their modules. Pinia stores wrap them for reactivity.
4. **shadcn-vue UI library** — `components/ui/` contains headless primitives built on `reka-ui`, each with a barrel `index.ts`. Import via `@/components/ui/<name>`.
5. **Snake_case API fields** — TypeScript interfaces use `snake_case` field names to match backend JSON, avoiding transform layers.
6. **Stable device identity** — device type comes from the UA (`phone` for mobile/tablet/wearable, otherwise `web`) unless `localStorage` `onlineim.device_type` is set. The id lives at `onlineim.device_id.{type}`. Login also sends `device_name`: `onlineim.device_name` if set, otherwise `桌面端` / `手机端` / `网页端` plus browser and OS. The device list renders `device_name` and falls back to the type label. `SESSION_KICKED` or close code `4001` clears the local session, does not call the logout API, and does not reconnect or send another `SYNC_REQUEST`. Close code `4000` means this device's newer page took the socket. Own messages, read cursors, `DRAFT_UPDATED` and `OFFLINE_SYNC` are applied together: a snapshot does not mark the user's own sends as unread, and another device's read cursor clears unread without marking outbound messages read by the peer. See [Device display name](../backend/device-display-name.md).

---

## Quick Reference

- Dev server: `http://localhost:3000`
- Backend API: `http://localhost:8080/api/v1`
- WebSocket: `ws://localhost:8081/api/v1/chat?token=Bearer%20<jwt>`
- Path alias: `@` → `./src`
- WebSocket protocol: `{ type: "<TYPE>", message: {<payload>} }`

---

**Language**: Documentation is written in English. UI text and code comments are in Chinese.
