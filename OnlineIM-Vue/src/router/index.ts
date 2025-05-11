import { createRouter, createWebHistory } from 'vue-router'
import Login from '@/views/Login.vue'
import Main from '@/views/Main.vue'
import Register from '@/views/Register.vue'
import Test from '@/components/independent/profile/GroupProfile.vue'
import AppSidebarRightTalks from '@/components/AppSideBar/right/AppSidebarRightTalks.vue'
import AppSidebarRightFriends from '@/components/AppSideBar/right/AppSidebarRightFriends.vue'
import AppSidebarRightGroup from '@/components/AppSideBar/right/AppSidebarRightGroup.vue'
import userFounding from '@/components/independent/founding/userFounding.vue'
import { useUserStore } from '@/stores/user'
import { toast } from 'vue-sonner';
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
      component: Main,
      children: [
        {
          path: 'chat',
          component: AppSidebarRightTalks
        },
        {
          path: 'friends',
          component: AppSidebarRightFriends
        },
        {
          path: 'groups',
          component: AppSidebarRightGroup
        },
        {
          path:'search',
          component:userFounding
        }
      ]
    },
    {
      path: '/register',
      name: 'Register',
      component: Register
    },
    {
      path:"/test",
      name:'test',
      component: Test
    }
  ]
})

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  
  if (to.path !== '/login'&&to.path!=='/register' && !userStore.loggedInUser.user_id) {
    next('/login')
    toast.error('用户未登录')
  } else if (to.path === '/login' && userStore.token) {
    next('/main/chat')
  } else {
    next()
  }
})

export default router