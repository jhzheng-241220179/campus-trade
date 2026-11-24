
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
import ProductEditView from '../views/ProductEditView.vue'
import AdminDashboardView from '../views/AdminDashboardView.vue'

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
      path: '/products/mine/:id/edit',
      name: 'product-edit',
      component: ProductEditView,
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/products/:id',
      name: 'product-detail',
      component: ProductDetailView,
    },
    {
      path: '/admin',
      name: 'admin-dashboard',
      component: AdminDashboardView,
      meta: {
        requiresAuth: true,
        requiresAdmin: true,
      },
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
      // 身份状态查询失败时，由后续判断决定是否允许进入。
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

  if (
    to.meta.requiresAdmin &&
    authStore.user?.role !== 'ADMIN'
  ) {
    return {
      name: 'home',
    }
  }

  return true
})

export default router
