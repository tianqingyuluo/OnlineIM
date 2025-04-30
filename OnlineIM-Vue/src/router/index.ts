import { createRouter, createWebHistory } from 'vue-router'
import Login from '@/views/Login.vue'
import Main from '@/components/Main.vue'
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: Login
    },
    {
      path: '/main',
      name: 'Main',
      component: Main
    },
  ]
})

export default router