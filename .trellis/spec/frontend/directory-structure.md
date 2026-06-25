# Directory Structure

> How frontend code is organized in `OnlineIM-Vue`.

---

## Overview

Vue 3 + Vite single-page application. All source code lives under `src/` with a flat, layer-based directory structure. The project uses an `@` path alias pointing to `./src`.

---

## Directory Layout

```
OnlineIM-Vue/
├── index.html                  # HTML entry point
├── vite.config.ts              # Vite config: vue + tailwindcss plugins, @ alias, port 3000
├── tsconfig.json               # TypeScript config (references app + node configs)
├── package.json                # Scripts: dev, build, preview, proxy, dev-all
├── proxy-server.js             # Express dev proxy (port 8000 → backend API)
└── src/
    ├── main.ts                 # App entry: createApp, Pinia + persistedstate, router
    ├── App.vue                 # Root: <router-view/> + <Toaster> (vue-sonner)
    ├── config.ts               # API_BASE_URL constant (hardcoded)
    ├── style.css               # Global styles + Tailwind import
    ├── vite-env.d.ts           # Vite type declarations
    ├── router/
    │   └── index.ts            # Vue Router config + beforeEach auth guard
    ├── views/                  # Page-level components
    ├── components/
    │   ├── ui/                 # shadcn-vue style UI primitives (button, input, form, select, ...)
    │   ├── MainPart/           # Chat main area components (UserMainPart, GroupMainPart, ...)
    │   ├── AppSideBar/         # Sidebar layout components
    │   ├── independent/        # Standalone feature components
    │   └── *.vue               # Feature dialog/panel components
    ├── services/               # API + WebSocket service modules
    ├── stores/                 # Pinia stores
    ├── type/                   # TypeScript interface/type definitions (mirror API models)
    ├── utils/                  # Utility functions (IndexedDB, time, etc.)
    └── lib/
        └── utils.ts            # cn() Tailwind class merge helper
```

---

## Layer Responsibilities

| Directory | Responsibility | Pattern |
|-----------|---------------|---------|
| `views/` | Top-level pages mapped to routes | `<script setup lang="ts">`, route components |
| `components/` | Reusable UI components | Split into `ui/` (primitives) + feature groups |
| `components/ui/` | shadcn-vue headless UI primitives | Each component = directory with `index.ts` barrel + `.vue` files, built on `reka-ui` |
| `services/` | HTTP API calls + WebSocket management | Plain object/class exports, use shared `api` axios instance |
| `stores/` | Global state (Pinia) | `defineStore` with options API style, `persist: true` |
| `type/` | TypeScript types matching backend API responses | `interface` / `type` exports, `snake_case` field names |
| `utils/` | Pure utility functions | IndexedDB wrapper, time helpers |
| `lib/` | Framework-specific helpers | `cn()` for Tailwind class merging |

---

## Module Organization

- **Views are route components** — each view corresponds to a route in `router/index.ts`. Views compose components from `components/`.
- **Components are grouped by feature area**, not by type. `components/ui/` holds design-system primitives; `components/MainPart/` holds chat-area components; `components/AppSideBar/` holds sidebar components.
- **Services are one-per-resource** — `auth.service.ts`, `message.service.ts`, `group.service.ts`, etc. Each exports a singleton object or class.
- **Stores are one-per-domain** — `user.ts`, `list.ts`, `history.ts`, `websocketStore.ts`, `notificationStore.ts`.

---

## Naming Conventions

| Artifact | Convention | Example |
|----------|-----------|---------|
| Vue component file | PascalCase.vue | `UserMainPart.vue`, `AddGroupDialog.vue` |
| UI primitive directory | kebab-case | `components/ui/select/`, `components/ui/form/` |
| UI barrel export | `index.ts` | re-exports all `.vue` components in the directory |
| Service file | `<resource>.service.ts` | `message.service.ts`, `auth.service.ts` |
| Store file | `<name>.ts` | `user.ts`, `history.ts` |
| Type file | `<Resource>.ts` (PascalCase) | `User.ts`, `message.ts`, `Conversation.ts` |
| API field names | `snake_case` | `user_id`, `conversation_id`, `seq_id` |
| TypeScript interfaces | PascalCase | `MessageResponse`, `UserSearchResult` |
| Pinia store id | kebab-case string | `'user'`, `'list'`, `'history'`, `'websocket'` |

---

## Path Alias

- `@` → `./src` (configured in `vite.config.ts`)
- Use `@/` for all intra-project imports: `import { useUserStore } from '@/stores/user'`
- **Do not use relative paths** like `../../stores/user` — use `@/stores/user` instead.

---

## Examples

- Page component: `views/Login.vue` — VeeValidate + Zod form, composes `ui/button`, `ui/form`, `ui/input`.
- Chat main area: `components/MainPart/UserMainPart.vue`, `components/MainPart/GroupMainPart.vue`.
- Service module: `services/api.service.ts` (axios instance + interceptors), `services/message.service.ts` (resource API).
- Pinia store: `stores/user.ts` (auth state + actions), `stores/history.ts` (message history + IndexedDB sync).
- Type definition: `type/message.ts` (`MessageResponse` interface matching backend response).
- UI primitive: `components/ui/select/` (barrel `index.ts` + 11 `.vue` components built on `reka-ui`).
