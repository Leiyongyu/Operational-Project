import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          redirect: '/dashboard',
        },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/UserManagementView.vue'),
          meta: { requiresAdmin: true },
        },
        {
          path: 'purchase-plan/create',
          name: 'purchasePlanCreate',
          component: () => import('@/views/PurchasePlanCreateView.vue'),
        },
        {
          path: 'purchases',
          name: 'purchases',
          component: () => import('@/views/PurchaseListView.vue'),
        },
        {
          path: 'brand-owners',
          name: 'brandOwners',
          component: () => import('@/views/BrandOwnerView.vue'),
          meta: { requiresAdmin: true },
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    return {
      name: 'login',
      query: { redirect: to.fullPath },
    }
  }

  if (to.meta.guestOnly && authStore.isLoggedIn) {
    return { name: 'dashboard' }
  }

  if (to.meta.requiresAdmin && !authStore.isAdmin) {
    return { name: 'dashboard' }
  }
})

export default router
