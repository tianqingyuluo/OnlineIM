import { createRouter, createWebHistory } from 'vue-router'
import Login from '@/views/Login.vue'
import Main from '@/components/Main.vue'
import Register from '@/views/Register.vue'
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
    {
      path: '/register',
      name: 'Register',
      component: Register
    },
  ]
})

export default router