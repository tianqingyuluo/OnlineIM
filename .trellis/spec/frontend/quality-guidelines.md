# Quality Guidelines

> Code standards, conventions, and forbidden patterns for the frontend.

---

## Build & Verification

```bash
# Install dependencies (in OnlineIM-Vue/)
npm install

# Dev server (port 3000)
npm run dev

# Dev server + proxy (concurrent)
npm run dev-all

# Proxy server only (port 8000 → backend)
npm run proxy

# Type-check + build
npm run build

# Preview production build
npm run preview
```

- `npm run build` runs `vue-tsc -b && vite build` — **type errors fail the build**. Always type-check before committing.
- There is no linter or formatter configured (no ESLint/Prettier config in `package.json`). Maintain consistent style manually.

---

## Import Conventions

1. Use the `@` alias for all project-internal imports:
   ```ts
   // Correct
   import { useUserStore } from '@/stores/user'
   import api from '@/services/api.service'

   // Wrong
   import { useUserStore } from '../../stores/user'
   ```
2. Group imports: external libraries → `@/` internal → relative `./`.
3. Use `import type { ... }` for type-only imports.

---

## Console Logging

- The codebase uses `console.log`, `console.error`, `console.warn` extensively for debugging.
- **Do not leave `console.log` debug statements in production code.** Remove them before committing, or use a conditional logger.
- `console.error` is acceptable for caught errors that are not surfaced to the user.

---

## Error Handling in Services

- Services should `throw` errors (not swallow them) so callers can handle appropriately.
- The axios response interceptor normalizes errors into `{ code, message, status, ...data }`.
- **Do not** catch and return `undefined`/`null` on error — let the error propagate.

Reference antipattern: `services/auth.service.ts` `updateToken` catches the error, logs it, and re-throws — but the `console.error` message is wrong ("登出失败").

---

## Store Action Conventions

1. Actions that call APIs should be `async`.
2. Use `try/catch` with meaningful error handling — do not leave empty catch blocks.
3. Reset state to defaults on logout/clear, using deep clone for object defaults.
4. Cross-store calls: `const otherStore = useOtherStore()` inside an action is acceptable.

---

## Routing

- All authenticated routes are children of `/main` with a `beforeEach` guard checking `userStore.token`.
- Use lazy loading (`() => import(...)`) for non-critical route components.
- Route names should be PascalCase: `name: 'Login'`, `name: 'Main'`.

Reference: `router/index.ts` — the auth guard:
```ts
router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()
  if (to.path !== '/login' && to.path !== '/register' && !userStore.token) {
    next('/login')
    toast.error('用户未登录')
  } else if (to.path === '/login' && userStore.token) {
    next('/main/chat')
  } else {
    next()
  }
})
```

---

## Forbidden Patterns

| Pattern | Why | Fix |
|---------|-----|-----|
| Relative imports (`../../`) | Breaks on file moves, inconsistent | Use `@/` alias |
| Importing from `shared/config` | The `shared/` directory does not exist | Import from `@/config` |
| Empty catch blocks (`catch (e) {}`) | Hides errors silently | At minimum `console.error(e)` |
| `any` type without justification | Loses type safety | Define proper interfaces |
| Inline magic numbers/strings | Hard to maintain | Extract to named constants |
| Storing non-serializable objects in persisted stores | `pinia-plugin-persistedstate` uses `localStorage` | Only persist plain data |

---

## Configuration

- `src/config.ts` holds `API_BASE_URL` (currently hardcoded to `http://localhost:8080/api/v1`).
- `vite.config.ts` configures the dev server (host `0.0.0.0`, port 3000) and `@` alias.
- `proxy-server.js` is an Express dev proxy (port 8000) forwarding to the backend — used for mobile dev (`phoneMain.vue`).
- **No environment variable system** — `API_BASE_URL` and `WS_API_URL` should be moved to `.env` files (`VITE_API_BASE_URL`, `VITE_WS_API_URL`) for environment-specific builds.

---

## Known Issues (Avoid Reproducing)

1. **`shared/config.ts` does not exist** — `api.service.ts`, `websocket.service.ts`, and `proxy-server.js` all import from `shared/config`. The real config is `src/config.ts` and it only exports `API_BASE_URL` (no `WS_API_URL`). This is a build-breaking bug that must be fixed.

2. **Duplicate Pinia setup** in `main.ts` and `stores/index.ts`. Only `main.ts` is used; `stores/index.ts` is dead code.

3. **`src/test.vue`** is a scratch file at the src root — remove it.

4. **`utils/heiheihei.ts`** — unclear naming. Rename to reflect its purpose (it exports `simpleMusicPlayer`).

5. **No ESLint/Prettier configuration** — consider adding for consistent formatting and catching common bugs.

6. **Service file naming inconsistency**: `friendGroups.servise.ts` has a typo ("servise" instead of "service"). Rename to `friendGroups.service.ts`.
