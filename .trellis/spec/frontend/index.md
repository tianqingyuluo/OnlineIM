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

---

## Key Architecture Decisions

1. **Options API Pinia stores** — all stores use `defineStore('id', { state, actions, getters })`, not the setup function style.
2. **Dual-layer cache** — Pinia (in-memory + localStorage persistence) + IndexedDB (offline message/conversation cache). `useListStore` loads from IndexedDB first for instant display, then syncs from API.
3. **Singleton services** — `websocketService` and `TokenService` are singletons exported from their modules. Pinia stores wrap them for reactivity.
4. **shadcn-vue UI library** — `components/ui/` contains headless primitives built on `reka-ui`, each with a barrel `index.ts`. Import via `@/components/ui/<name>`.
5. **Snake_case API fields** — TypeScript interfaces use `snake_case` field names to match backend JSON, avoiding transform layers.

---

## Quick Reference

- Dev server: `http://localhost:3000`
- Backend API: `http://localhost:8080/api/v1`
- WebSocket: `ws://localhost:8081/api/v1/chat?token=Bearer%20<jwt>`
- Path alias: `@` → `./src`
- WebSocket protocol: `{ type: "<TYPE>", message: {<payload>} }`

---

**Language**: Documentation is written in English. UI text and code comments are in Chinese.
