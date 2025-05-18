import { createRouter, createWebHistory } from 'vue-router'
import Login from '@/views/Login.vue'
import Main from '@/views/Main.vue'
import Register from '@/views/Register.vue'
import Test from '@/components/independent/friends/SendFriendRequest.vue'
import AppSidebarRightTalks from '@/components/AppSideBar/right/AppSidebarRightTalks.vue'
import AppSidebarRightFriends from '@/components/AppSideBar/right/AppSidebarRightFriends.vue'
import AppSidebarRightGroup from '@/components/AppSideBar/right/AppSidebarRightGroup.vue'
import userFounding from '@/components/independent/founding/userFounding.vue'
import UserMainPart from "@/components/MainPart/UserMainPart.vue";
import GroupMainPart from '@/components/MainPart/GroupMainPart.vue'
import { useUserStore } from '@/stores/user'
import { toast } from 'vue-sonner';
import phoneMain from '@/views/phoneMain.vue'

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
      component: window.innerWidth < 600 ? phoneMain : Main,
      children: [
        {
          path: 'chat',
          component: AppSidebarRightTalks,
          children:[
            {
              path: 'private/:id',  // 私聊路径
              component: UserMainPart,
              props: true
            },
            {
              path: 'group/:id',    // 群聊路径
              name:'chat',
              component: GroupMainPart,
              props: true
            }
          ]
        },
        {
          path: 'friends',
          component: AppSidebarRightFriends,
          children:[
            {
              path: ':userId',
              name: 'friend',
              component: () => import('@/views/userPart.vue')
            }
          ]
        },
        {
          path: 'groups',
          component: AppSidebarRightGroup
        },
        {
          path:'search',
          component:userFounding
        },
        
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
  } else if (to.path === '/login' && userStore.loggedInUser.user_id) {
    next('/main/chat')
  } else {
    next()
  }
})

export default router