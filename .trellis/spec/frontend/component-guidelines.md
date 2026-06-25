# Component Guidelines

> Vue 3 component patterns, composition, and conventions.

---

## Script Setup

All components use `<script setup lang="ts">` (Composition API with TypeScript). No Options API usage.

Reference: `views/Login.vue`, `App.vue`
```vue
<script setup lang="ts">
import { Button } from '@/components/ui/button'
import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
</script>
```

---

## Component Categories

### 1. UI Primitives (`components/ui/`)

shadcn-vue style design system built on `reka-ui` (headless UI). Each primitive is a directory containing:

- Multiple `.vue` files (one per sub-component).
- An `index.ts` barrel file re-exporting all components.

Reference: `components/ui/select/index.ts`
```ts
export { default as Select } from './Select.vue'
export { default as SelectContent } from './SelectContent.vue'
export { default as SelectItem } from './SelectItem.vue'
// ...
```

Existing primitives: `button`, `input`, `form`, `select`, `sheet`, `skeleton`, `separator`, `textarea`, `tooltip`, and more.

**Convention**: When adding a new UI primitive, create a directory under `components/ui/`, add `.vue` files, and an `index.ts` barrel. Import via `@/components/ui/<name>`.

### 2. Feature Components

Business components grouped by area:
- `components/MainPart/` — chat area (`UserMainPart.vue`, `GroupMainPart.vue`, `UserTextArea.vue`, `tools.vue`)
- `components/AppSideBar/` — sidebar layout (with `right/` sub-directory)
- `components/independent/` — standalone feature components
- Root-level `components/*.vue` — dialogs and panels (`AddGroupDialog.vue`, `Announcement.vue`, `EditGroupDialog.vue`, etc.)

### 3. Page Views (`views/`)

Top-level route components: `Login.vue`, `Register.vue`, `Main.vue`, `ChatContainer.vue`, `choiceOne.vue`, `userPart.vue`, `groupPart.vue`, `phoneMain.vue`.

---

## Props and Emits

- Use `defineProps<T>()` with TypeScript interface for typed props.
- Use `withDefaults()` for default values.
- Use `defineEmits<T>()` for typed events.

Route params are received via `props: true` in router config (e.g. `private/:id` passes `id` as prop).

Reference: `router/index.ts`
```ts
{ path: 'private/:id', component: UserMainPart, props: true }
```

---

## Styling

- **Tailwind CSS 4** via `@tailwindcss/vite` plugin (no `tailwind.config.js` — CSS-first config).
- Utility classes directly in templates.
- `cn()` helper from `@/lib/utils` for conditional class merging (clsx + tailwind-merge).
- `reka-ui` for accessible headless UI primitives.
- `lucide-vue-next` for icons (imported as PascalCase components: `<LogIn />`).

Reference: `views/Login.vue`
```vue
<Input class="border-gray-200 focus:border-gray-500 focus:ring-gray-500" />
<Button class="w-full bg-black hover:bg-gray-900">登录</Button>
```

---

## Toast Notifications

- `vue-sonner` for toast notifications.
- Global `<Toaster>` mounted in `App.vue`.
- Import `toast` from `vue-sonner` and call `toast.error(...)`, `toast.success(...)`.

Reference: `App.vue`, `stores/list.ts`
```ts
import { toast } from 'vue-sonner';
toast.error('分组不为空，无法删除');
toast.success(`已将 ${count} 个好友移动到默认分组`);
```

---

## Form Validation

- **VeeValidate 4** + **@vee-validate/zod** + **Zod** for form validation.
- Pattern: `toTypedSchema(z.object({...}))` → `useForm({ validationSchema })` → `<FormField>` components.

Reference: `views/Login.vue`
```vue
<script setup lang="ts">
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import * as z from 'zod'

const formSchema = toTypedSchema(z.object({
  username: z.string().min(4).max(16),
  password: z.string().min(6).max(20),
}))
const { handleSubmit } = useForm({ validationSchema: formSchema, initialValues: { username: '', password: '' } })
const onSubmit = handleSubmit(async (values) => { ... })
</script>

<template>
  <FormField v-slot="{ componentField, errorMessage }" name="username">
    <FormItem>
      <FormLabel>用户名</FormLabel>
      <FormControl><Input v-bind="componentField" /></FormControl>
      <FormMessage>{{ errorMessage }}</FormMessage>
    </FormItem>
  </FormField>
</template>
```

---

## Known Issues (Avoid Reproducing)

1. **Inconsistent file naming**: Some view files use camelCase (`choiceOne.vue`, `groupPart.vue`, `userPart.vue`, `phoneMain.vue`) instead of PascalCase. New components should use PascalCase.

2. **`test.vue` at `src/` root** is a leftover test file — do not add scratch files to `src/` root.

3. **Route for `/test`** points to a component (`SendFriendRequest.vue`) that is a feature component, not a test page. Remove test routes from production router config.
