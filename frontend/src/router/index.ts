
import {
  createRouter,
  createWebHistory,
} from 'vue-router'

import HomeView from '../views/HomeView.vue'
import RouteDemoView from '../views/RouteDemoView.vue'
import RegisterView from '../views/RegisterView.vue'
import VerifyEmailView from '../views/VerifyEmailView.vue'
import LoginView from '../views/LoginView.vue'

const router = createRouter({
  history: createWebHistory(),

  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/route-demo',
      name: 'route-demo',
      component: RouteDemoView,
    },
    {
      path: '/register',
      name: 'register',
      component: RegisterView,
    },
    {
      path: '/verify',
      name: 'verify',
      component: VerifyEmailView,
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
    },
  ],
})

export default router
