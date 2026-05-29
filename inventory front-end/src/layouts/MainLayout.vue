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
const roleLabel = computed(() => (authStore.isAdmin ? '管理员' : '用户'))
const roleType = computed(() => (authStore.isAdmin ? 'error' : 'info'))
const canManageUsers = computed(() => authStore.isAdmin)
const platformOptions = computed(() =>
  uiStore.platformOptions.map((platform) => ({ label: platform, value: platform }))
)

const activeKey = computed(() => route.path)

const menuOptions = computed(() => {
  const items = [
    { label: '运营组', key: '/dashboard' },
  ]
  if (canManageUsers.value) {
    items.push({ label: '用户管理', key: '/users' })
  }
  items.push({ label: '创建采购计划', key: '/purchase-plan/create' })
  return items
})

function handleMenuUpdate(key) {
  if (key === '/dashboard') {
    router.push({ name: 'dashboard' })
  } else if (key === '/users') {
    router.push({ name: 'users' })
  } else if (key === '/purchases') {
    router.push({ name: 'purchases' })
  } else if (key === '/brand-owners') {
    router.push({ name: 'brandOwners' })
  }
}

function renderMenuIcon(iconName) {
  const icons = {
    dashboard: h(
      'svg',
      { viewBox: '0 0 24 24', width: '18', height: '18', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' },
      [h('rect', { x: '3', y: '3', width: '7', height: '7', rx: '1' }),
       h('rect', { x: '14', y: '3', width: '7', height: '7', rx: '1' }),
       h('rect', { x: '3', y: '14', width: '7', height: '7', rx: '1' }),
       h('rect', { x: '14', y: '14', width: '7', height: '7', rx: '1' })]
    ),
    users: h(
      'svg',
      { viewBox: '0 0 24 24', width: '18', height: '18', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' },
      [h('path', { d: 'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2' }),
       h('circle', { cx: '9', cy: '7', r: '4' }),
       h('path', { d: 'M23 21v-2a4 4 0 0 0-3-3.87' }),
       h('path', { d: 'M16 3.13a4 4 0 0 1 0 7.75' })]
    ),
    purchase: h(
      'svg',
      { viewBox: '0 0 24 24', width: '18', height: '18', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' },
      [h('path', { d: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z' }),
       h('polyline', { points: '14,2 14,8 20,8' }),
       h('line', { x1: '12', y1: '18', x2: '12', y2: '12' }),
       h('line', { x1: '9', y1: '15', x2: '15', y2: '15' })]
    ),
    brand: h(
      'svg',
      { viewBox: '0 0 24 24', width: '18', height: '18', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' },
      [h('path', { d: 'M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z' }),
       h('line', { x1: '7', y1: '7', x2: '7.01', y2: '7' })]
    ),
  }
  return () => h(NIcon, null, { default: () => icons[iconName] })
}

// Build menu options with icons
const menuWithIcons = computed(() => {
  const items = [
    { label: '运营组', key: '/dashboard', icon: renderMenuIcon('dashboard') },
  ]
  if (canManageUsers.value) {
    items.push({ label: '用户管理', key: '/users', icon: renderMenuIcon('users') })
    items.push({ label: '品牌负责人', key: '/brand-owners', icon: renderMenuIcon('brand') })
  }
  items.push({ label: '采购', key: '/purchases', icon: renderMenuIcon('purchase') })
  return items
})

async function handleLogout() {
  await authStore.logout()
  await router.push('/login')
}
</script>

<template>
  <NLayout has-sider position="absolute" style="min-height: 100vh">
    <NLayoutSider
      bordered
      collapse-mode="width"
      :collapsed-width="64"
      :width="200"
      :collapsed="collapsed"
      show-trigger="arrow"
      @collapse="collapsed = true"
      @expand="collapsed = false"
    >
      <!-- Logo area -->
      <div class="sider-logo">
        <div class="logo-icon">EMP</div>
        <span v-show="!collapsed" class="logo-text">电商中台</span>
      </div>

      <NMenu
        :value="activeKey"
        :collapsed="collapsed"
        :collapsed-width="64"
        :collapsed-icon-size="20"
        :options="menuWithIcons"
        inverted
        @update:value="handleMenuUpdate"
      />
    </NLayoutSider>

    <NLayout>
      <NLayoutHeader bordered class="layout-header">
        <div class="header-left">
          <h2 class="header-title">跨境电商运营中台</h2>
        </div>

        <div class="header-right">
          <div class="platform-switch">
            <label for="platform">当前平台</label>
            <NSelect
              id="platform"
              v-model:value="selectedPlatform"
              :options="platformOptions"
              size="small"
              style="width: 130px"
            />
          </div>

          <NSpace align="center" size="small">
            <span class="user-name">{{ userName }}</span>
            <NTag size="small" :bordered="false" :type="roleType">
              {{ roleLabel }}
            </NTag>
            <NButton size="small" @click="handleLogout">退出登录</NButton>
          </NSpace>
        </div>
      </NLayoutHeader>

      <NLayoutContent content-style="padding: 24px; background: #f0f2f5;">
        <RouterView v-slot="{ Component }">
          <KeepAlive>
            <component :is="Component" />
          </KeepAlive>
        </RouterView>
      </NLayoutContent>
    </NLayout>
  </NLayout>
</template>

<style scoped>
.sider-logo {
  display: flex;
  align-items: center;
  height: 60px;
  padding: 0 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  overflow: hidden;
  white-space: nowrap;
}

.logo-icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  min-width: 36px;
  border-radius: 8px;
  background: linear-gradient(135deg, #1890ff 0%, #096dd9 100%);
  color: #fff;
  font-weight: 700;
  font-size: 14px;
}

.logo-text {
  margin-left: 12px;
  font-size: 16px;
  font-weight: 600;
  color: #fff;
}

.layout-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 48px;
  padding: 0 24px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
}

.header-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.platform-switch {
  display: flex;
  align-items: center;
  gap: 8px;
}

.platform-switch label {
  color: rgba(0, 0, 0, 0.65);
  font-size: 13px;
  white-space: nowrap;
}

.user-name {
  font-size: 14px;
  color: rgba(0, 0, 0, 0.65);
}

/* Override sidebar background to dark */
:deep(.n-layout-sider) {
  background: #001529 !important;
}
</style>
