import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import './style.css'
import App from './App.vue'
import router from './router/index.ts'
import { bootstrapTauriDesktop, isTauriRuntime } from '@/services/tauri-bridge'

const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)
const app = createApp(App)

app.use(pinia)
app.use(router)
if (isTauriRuntime()) {
  void bootstrapTauriDesktop()
}
app.mount('#app')

