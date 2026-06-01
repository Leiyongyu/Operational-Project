<script setup>
import {
  NConfigProvider,
  NDialogProvider,
  NMessageProvider,
  NNotificationProvider,
  zhCN,
  dateZhCN,
  darkTheme,
} from 'naive-ui'
import { ref } from 'vue'

// 自定义主题覆盖 Naive UI 默认样式
const themeOverrides = {
  common: {
    primaryColor: '#1677ff',
    primaryColorHover: '#4096ff',
    primaryColorPressed: '#0958d9',
    primaryColorSuppl: '#1677ff',
    borderRadius: '6px',
    borderRadiusSmall: '4px',
    fontSize: '14px',
    fontWeightStrong: '600',
  },
  Layout: {
    siderBorderColor: 'transparent',
  },
  Menu: {
    itemTextColorHorizontalInverted: 'rgba(255,255,255,0.70)',
    itemTextColorHoverHorizontalInverted: '#fff',
    itemTextColorActiveHorizontalInverted: '#fff',
    itemColorActiveHorizontalInverted: '#1677ff',
    itemIconColorActiveHorizontalInverted: '#fff',
    groupTextColorInverted: 'rgba(255,255,255,0.40)',
  },
  Button: {
    borderRadiusSmall: '4px',
    borderRadiusMedium: '6px',
  },
  Card: {
    borderRadius: '8px',
    paddingMedium: '20px 24px',
    titleFontSizeMedium: '16px',
    titleTextColor: 'rgba(0,0,0,0.85)',
  },
  DataTable: {
    thColor: '#fafafa',
    thFontWeight: '600',
    tdPaddingMedium: '10px 12px',
    thPaddingMedium: '10px 12px',
  },
  Tag: {
    borderRadius: '4px',
  },
  Input: {
    borderRadius: '6px',
  },
  Select: {
    peers: {
      InternalSelection: {
        borderRadius: '6px',
      },
    },
  },
}
</script>

<template>
  <NConfigProvider :locale="zhCN" :date-locale="dateZhCN" :theme-overrides="themeOverrides">
    <NNotificationProvider>
      <NMessageProvider>
        <NDialogProvider>
          <RouterView />
        </NDialogProvider>
      </NMessageProvider>
    </NNotificationProvider>
  </NConfigProvider>
</template>

<style>
/* ===== 全局基础样式 ===== */
html,
body,
#app {
  height: 100%;
  margin: 0;
}

body {
  background: #f0f2f5;
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
    'Hiragino Sans GB', 'Microsoft YaHei', 'Helvetica Neue', Helvetica, Arial,
    sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* ===== 卡片悬停微交互 ===== */
.n-card {
  transition: box-shadow 0.3s ease, transform 0.2s ease;
}

.n-card:not(.n-card--no-hover):hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08) !important;
}

/* ===== 侧边栏基础样式 ===== */
.n-layout-sider {
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1) !important;
}

/* ===== 滚动条美化 ===== */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  border-radius: 3px;
  background-color: rgba(0, 0, 0, 0.12);
  transition: background-color 0.2s;
}

::-webkit-scrollbar-thumb:hover {
  background-color: rgba(0, 0, 0, 0.22);
}

/* ===== 通用页面容器动画 ===== */
.dashboard-page,
.users-page {
  animation: content-fade-in 0.35s ease both;
}

@keyframes content-fade-in {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ===== 数值跳动动画 ===== */
@keyframes count-up {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.animate-count {
  animation: count-up 0.5s ease both;
}
</style>
