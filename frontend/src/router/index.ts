
import {
  createRouter,
  createWebHistory,
} from 'vue-router'

import { useAuthStore } from '../stores/auth'

import HomeView from '../views/HomeView.vue'
import RouteDemoView from '../views/RouteDemoView.vue'
import RegisterView from '../views/RegisterView.vue'
import VerifyEmailView from '../views/VerifyEmailView.vue'
import LoginView from '../views/LoginView.vue'
import ProductListView from '../views/ProductListView.vue'
import ProductDetailView from '../views/ProductDetailView.vue'
import ProductPublishView from '../views/ProductPublishView.vue'
import MyProductsView from '../views/MyProductsView.vue'

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
    {
      path: '/products',
      name: 'products',
      component: ProductListView,
    },
    {
      path: '/products/new',
      name: 'product-new',
      component: ProductPublishView,
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/products/mine',
      name: 'my-products',
      component: MyProductsView,
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/products/:id',
      name: 'product-detail',
      component: ProductDetailView,
    },
  ],
})

router.beforeEach(async (to) => {
  if (!to.meta.requiresAuth) {
    return true
  }

  const authStore = useAuthStore()

  if (!authStore.initialized) {
    try {
      await authStore.fetchCurrentUser()
    } catch {
      // Session 失效或者尚未登录时，交给下方判断。
    }
  }

  if (!authStore.isAuthenticated) {
    return {
      name: 'login',
      query: {
        redirect: to.fullPath,
      },
    }
  }

  return true
})

export default router
