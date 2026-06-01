<script setup>
import { computed, h, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter, useRoute } from 'vue-router'
import {
  NButton,
  NIcon,
  NLayout,
  NLayoutContent,
  NLayoutHeader,
  NLayoutSider,
  NMenu,
  NSelect,
  NSpace,
  NTag,
  NDropdown,
  NAvatar,
} from 'naive-ui'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const uiStore = useUiStore()
const { selectedPlatform } = storeToRefs(uiStore)

const collapsed = ref(false)
const userName = computed(() => authStore.user?.ownerName || authStore.user?.name || '未设置')
const userAccount = computed(() => authStore.user?.account || '')
const roleLabel = computed(() => (authStore.isAdmin ? '管理员' : '用户'))
const roleType = computed(() => (authStore.isAdmin ? 'error' : 'info'))
const canManageUsers = computed(() => authStore.isAdmin)
const platformOptions = computed(() =>
  uiStore.platformOptions.map((platform) => ({ label: platform, value: platform }))
)

const activeKey = computed(() => route.path)

// 构建菜单项 —— 使用 router-link 风格的 key 由 handleMenuUpdate 统一派发
const menuOptions = computed(() => {
  const base = [
    {
      label: '运营组',
      key: '/dashboard',
      icon: renderMenuIcon('dashboard'),
    },
  ]
  if (canManageUsers.value) {
    base.push(
      { label: '用户管理', key: '/users', icon: renderMenuIcon('users') },
      { label: '品牌负责人', key: '/brand-owners', icon: renderMenuIcon('brand') },
    )
  }
  base.push(
    { label: '采购管理', key: '/purchases', icon: renderMenuIcon('purchase') },
  )
  return base
})

// 路由映射表
const routeMap = {
  '/dashboard': 'dashboard',
  '/users': 'users',
  '/brand-owners': 'brandOwners',
  '/purchases': 'purchases',
}

function handleMenuUpdate(key) {
  const name = routeMap[key]
  if (name) {
    router.push({ name })
  }
}

// 用户下拉菜单
const userDropdownOptions = [
  {
    label: '退出登录',
    key: 'logout',
    icon: () =>
      h(
        'svg',
        { viewBox: '0 0 24 24', width: '16', height: '16', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' },
        [
          h('path', { d: 'M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4' }),
          h('polyline', { points: '16,17 21,12 16,7' }),
          h('line', { x1: '21', y1: '12', x2: '9', y2: '12' }),
        ],
      ),
  },
]

function handleUserSelect(key) {
  if (key === 'logout') {
    handleLogout()
  }
}

// SVG 图标渲染
function renderMenuIcon(name) {
  const icons = {
    dashboard: h(
      'svg',
      {
        viewBox: '0 0 24 24', width: '20', height: '20', fill: 'none',
        stroke: 'currentColor', 'stroke-width': '2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round',
      },
      [
        h('rect', { x: '3', y: '3', width: '7', height: '7', rx: '1' }),
        h('rect', { x: '14', y: '3', width: '7', height: '7', rx: '1' }),
        h('rect', { x: '3', y: '14', width: '7', height: '7', rx: '1' }),
        h('rect', { x: '14', y: '14', width: '7', height: '7', rx: '1' }),
      ],
    ),
    users: h(
      'svg',
      {
        viewBox: '0 0 24 24', width: '20', height: '20', fill: 'none',
        stroke: 'currentColor', 'stroke-width': '2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round',
      },
      [
        h('path', { d: 'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2' }),
        h('circle', { cx: '9', cy: '7', r: '4' }),
        h('path', { d: 'M23 21v-2a4 4 0 0 0-3-3.87' }),
        h('path', { d: 'M16 3.13a4 4 0 0 1 0 7.75' }),
      ],
    ),
    purchase: h(
      'svg',
      {
        viewBox: '0 0 24 24', width: '20', height: '20', fill: 'none',
        stroke: 'currentColor', 'stroke-width': '2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round',
      },
      [
        h('path', { d: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z' }),
        h('polyline', { points: '14,2 14,8 20,8' }),
        h('line', { x1: '16', y1: '13', x2: '8', y2: '13' }),
        h('line', { x1: '16', y1: '17', x2: '8', y2: '17' }),
      ],
    ),
    brand: h(
      'svg',
      {
        viewBox: '0 0 24 24', width: '20', height: '20', fill: 'none',
        stroke: 'currentColor', 'stroke-width': '2', 'stroke-linecap': 'round', 'stroke-linejoin': 'round',
      },
      [
        h('path', { d: 'M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z' }),
        h('line', { x1: '7', y1: '7', x2: '7.01', y2: '7' }),
      ],
    ),
  }
  return () => h(NIcon, null, { default: () => icons[name] })
}

async function handleLogout() {
  await authStore.logout()
  await router.push('/login')
}
</script>

<template>
  <NLayout has-sider position="absolute" class="main-layout">
    <!-- ===== 侧边栏 ===== -->
    <NLayoutSider
      bordered
      collapse-mode="width"
      :collapsed-width="64"
      :width="220"
      :collapsed="collapsed"
      show-trigger="arrow"
      class="app-sider"
      @collapse="collapsed = true"
      @expand="collapsed = false"
    >
      <!-- Logo -->
      <div class="sider-logo">
        <div class="logo-icon">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
          </svg>
        </div>
        <Transition name="logo-text-fade">
          <span v-show="!collapsed" class="logo-text">电商运营中台</span>
        </Transition>
      </div>

      <!-- 导航菜单 -->
      <div class="sider-menu-wrap">
        <NMenu
          :value="activeKey"
          :collapsed="collapsed"
          :collapsed-width="64"
          :collapsed-icon-size="22"
          :options="menuOptions"
          :indent="24"
          inverted
          @update:value="handleMenuUpdate"
        />
      </div>
    </NLayoutSider>

    <!-- ===== 主内容区 ===== -->
    <NLayout>
      <!-- 顶部栏 -->
      <NLayoutHeader bordered class="layout-header">
        <div class="header-left">
          <div class="breadcrumb-current">{{ route.meta?.title || '' }}</div>
        </div>

        <div class="header-right">
          <!-- 平台切换 -->
          <div class="platform-switch">
            <span class="platform-label">平台</span>
            <NSelect
              v-model:value="selectedPlatform"
              :options="platformOptions"
              size="small"
              style="width: 110px"
            />
          </div>

          <!-- 用户区域 -->
          <NDropdown
            trigger="click"
            :options="userDropdownOptions"
            @select="handleUserSelect"
          >
            <div class="user-trigger">
              <NAvatar
                size="small"
                round
                :style="{ background: 'linear-gradient(135deg, #1677ff, #722ed1)' }"
              >
                {{ userName.charAt(0) }}
              </NAvatar>
              <span class="user-name">{{ userName }}</span>
              <NTag size="tiny" :bordered="false" :type="roleType" class="role-tag">
                {{ roleLabel }}
              </NTag>
            </div>
          </NDropdown>
        </div>
      </NLayoutHeader>

      <!-- 内容区域 -->
      <NLayoutContent
        content-style="padding: 24px; background: #f0f2f5; min-height: calc(100vh - 50px);"
        class="layout-content"
      >
        <RouterView />
      </NLayoutContent>
    </NLayout>
  </NLayout>
</template>

<style scoped>
/* ===== 布局 ===== */
.main-layout {
  min-height: 100vh;
}

/* ===== 侧边栏 ===== */
.app-sider {
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.app-sider :deep(.n-layout-sider-scroll-container) {
  display: flex;
  flex-direction: column;
}

.sider-logo {
  display: flex;
  align-items: center;
  height: 56px;
  padding: 0 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  overflow: hidden;
  white-space: nowrap;
  flex-shrink: 0;
}

.logo-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  min-width: 34px;
  border-radius: 10px;
  background: linear-gradient(135deg, #1677ff 0%, #722ed1 100%);
  color: #fff;
  box-shadow: 0 2px 8px rgba(22, 119, 255, 0.35);
  transition: transform 0.3s ease;
}

.logo-icon:hover {
  transform: rotate(-10deg) scale(1.05);
}

.logo-text {
  margin-left: 10px;
  font-size: 15px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.02em;
}

.logo-text-fade-enter-active,
.logo-text-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.logo-text-fade-enter-from,
.logo-text-fade-leave-to {
  opacity: 0;
  transform: translateX(-6px);
}

.sider-menu-wrap {
  flex: 1;
  overflow-y: auto;
  padding-top: 4px;
}

/* ===== 侧边栏主题覆盖 ===== */
:deep(.n-layout-sider) {
  background: linear-gradient(180deg, #001529 0%, #002140 100%) !important;
}

:deep(.n-menu.n-menu--inverted .n-menu-item-content) {
  transition: all 0.2s ease;
}

:deep(.n-menu.n-menu--inverted .n-menu-item-content:hover) {
  background: rgba(255, 255, 255, 0.08) !important;
}

:deep(.n-menu.n-menu--inverted .n-menu-item-content--selected) {
  background: rgba(22, 119, 255, 0.2) !important;
}

:deep(.n-menu.n-menu--inverted .n-menu-item-content--selected::before) {
  background: #1677ff !important;
  border-radius: 0 3px 3px 0;
}

/* ===== 顶部栏 ===== */
.layout-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 50px;
  padding: 0 24px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.06);
  z-index: 20;
  transition: box-shadow 0.3s ease;
}

.header-left {
  display: flex;
  align-items: center;
}

.breadcrumb-current {
  font-size: 15px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.platform-switch {
  display: flex;
  align-items: center;
  gap: 8px;
}

.platform-label {
  color: rgba(0, 0, 0, 0.55);
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

/* ===== 用户触发器 ===== */
.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px 4px 4px;
  border-radius: 20px;
  cursor: pointer;
  transition: background 0.2s ease;
}

.user-trigger:hover {
  background: rgba(0, 0, 0, 0.04);
}

.user-name {
  font-size: 13px;
  font-weight: 500;
  color: rgba(0, 0, 0, 0.75);
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-tag {
  flex-shrink: 0;
}

/* ===== 响应式 ===== */
@media (max-width: 768px) {
  .layout-header {
    padding: 0 16px;
  }

  .header-right {
    gap: 12px;
  }

  .platform-switch {
    display: none;
  }
}
</style>
