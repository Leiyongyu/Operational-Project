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
          meta: { title: '补货' },
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/UserManagementView.vue'),
          meta: { requiresAdmin: true, title: '用户管理' },
        },
        {
          path: 'purchase-plan/create',
          name: 'purchasePlanCreate',
          component: () => import('@/views/PurchasePlanCreateView.vue'),
          meta: { title: '创建采购计划' },
        },
        {
          path: 'purchases',
          name: 'purchases',
          component: () => import('@/views/PurchaseListView.vue'),
          meta: { title: '采购管理' },
        },
        {
          path: 'brand-owners',
          name: 'brandOwners',
          component: () => import('@/views/BrandOwnerView.vue'),
          meta: { requiresAdmin: true, title: '品牌负责人' },
        },
        {
          path: 'daily-price-tracking',
          name: 'dailyPriceTracking',
          component: () => import('@/views/DailyPriceTrackingView.vue'),
          meta: { title: '每日跟价' },
        },
        {
          path: 'link-templates',
          name: 'linkTemplates',
          component: () => import('@/views/LinkTemplateView.vue'),
          meta: { requiresAdmin: true, title: '链接管理' },
        },
        {
          path: 'operation-logs',
          name: 'operationLogs',
          component: () => import('@/views/OperationLogView.vue'),
          meta: { requiresAdmin: true, title: '操作日志' },
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
