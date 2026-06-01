<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NInput, NIcon } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const form = reactive({
  username: '',
  password: '',
})

const errorMessage = ref('')
const isSubmitting = ref(false)
const showForm = ref(false)

onMounted(() => {
  // 延迟出现表单动画
  setTimeout(() => {
    showForm.value = true
  }, 150)
})

async function handleSubmit() {
  errorMessage.value = ''
  isSubmitting.value = true

  const result = await authStore.login(form.username, form.password)

  if (!result.success) {
    errorMessage.value = result.message
    isSubmitting.value = false
    return
  }

  const redirectPath =
    typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'

  await router.push(redirectPath)
  isSubmitting.value = false
}

// 自定义 SVG 图标（使用 h 渲染函数，兼容无 JSX 环境）
import { h as _h } from 'vue'

const svgAttrs = (size) => ({
  viewBox: '0 0 24 24',
  width: size,
  height: size,
  fill: 'none',
  stroke: 'currentColor',
  'stroke-width': '2',
  'stroke-linecap': 'round',
  'stroke-linejoin': 'round',
})

const UserIcon = () =>
  _h('svg', svgAttrs(18), [
    _h('path', { d: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2' }),
    _h('circle', { cx: '12', cy: '7', r: '4' }),
  ])

const LockIcon = () =>
  _h('svg', svgAttrs(18), [
    _h('rect', { x: '3', y: '11', width: '18', height: '11', rx: '2', ry: '2' }),
    _h('path', { d: 'M7 11V7a5 5 0 0 1 10 0v4' }),
  ])
</script>

<template>
  <div class="login-page">
    <!-- 动态背景装饰 -->
    <div class="bg-decoration">
      <div class="bg-shape bg-shape-1"></div>
      <div class="bg-shape bg-shape-2"></div>
      <div class="bg-shape bg-shape-3"></div>
      <div class="bg-grid"></div>
    </div>

    <!-- 左侧品牌面板 -->
    <section class="hero-panel" :class="{ visible: showForm }">
      <div class="hero-content">
        <div class="hero-badge">
          <span class="badge-dot"></span>
          Cross‑Border Operations Console
        </div>
        <h1 class="hero-title">
          跨境电商
          <span class="highlight">运营中台</span>
        </h1>
        <p class="hero-desc">
          一体化聚合订单、库存、广告与物流数据，帮助团队高效管理亚马逊、
          eBay、Temu、TikTok Shop 和独立站的全球运营。
        </p>

        <!-- 数据卡片 -->
        <div class="hero-stats">
          <div class="stat-item" style="animation-delay: 0.2s">
            <span class="stat-value">14+</span>
            <span class="stat-label">接入站点</span>
          </div>
          <div class="stat-item" style="animation-delay: 0.35s">
            <span class="stat-value">98.6%</span>
            <span class="stat-label">履约准时率</span>
          </div>
          <div class="stat-item" style="animation-delay: 0.5s">
            <span class="stat-value">23.4%</span>
            <span class="stat-label">转化提升</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 右侧登录卡片 -->
    <section class="login-section" :class="{ visible: showForm }">
      <div class="login-card">
        <div class="card-header">
          <div class="card-avatar">
            <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
              <circle cx="12" cy="7" r="4" />
            </svg>
          </div>
          <h2>欢迎回来</h2>
          <p>登录您的账号以继续</p>
        </div>

        <form class="login-form" @submit.prevent="handleSubmit">
          <div class="input-group">
            <label class="input-label">账号</label>
            <NInput
              v-model:value="form.username"
              size="large"
              placeholder="请输入账号"
              :disabled="isSubmitting"
              clearable
              round
            >
              <template #prefix>
                <NIcon :component="UserIcon" />
              </template>
            </NInput>
          </div>

          <div class="input-group">
            <label class="input-label">密码</label>
            <NInput
              v-model:value="form.password"
              type="password"
              size="large"
              placeholder="请输入密码"
              :disabled="isSubmitting"
              show-password-on="click"
              round
              @keyup.enter="handleSubmit"
            >
              <template #prefix>
                <NIcon :component="LockIcon" />
              </template>
            </NInput>
          </div>

          <Transition name="error-fade">
            <div v-if="errorMessage" class="error-alert">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10" />
                <line x1="15" y1="9" x2="9" y2="15" />
                <line x1="9" y1="9" x2="15" y2="15" />
              </svg>
              <span>{{ errorMessage }}</span>
            </div>
          </Transition>

          <NButton
            type="primary"
            size="large"
            :loading="isSubmitting"
            :disabled="!form.username || !form.password"
            block
            round
            attr-type="submit"
            class="submit-btn"
          >
            {{ isSubmitting ? '验证中...' : '登 录' }}
          </NButton>
        </form>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* ===== 页面布局 ===== */
.login-page {
  position: relative;
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.2fr 0.9fr;
  overflow: hidden;
  background: #f5f7fa;
}

/* ===== 动态背景装饰 ===== */
.bg-decoration {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.bg-shape {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.4;
  animation: float-shape 20s ease-in-out infinite;
}

.bg-shape-1 {
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(22, 119, 255, 0.25), transparent 70%);
  top: -150px;
  right: -100px;
  animation-delay: 0s;
}

.bg-shape-2 {
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(114, 46, 209, 0.18), transparent 70%);
  bottom: -120px;
  left: -80px;
  animation-delay: -7s;
}

.bg-shape-3 {
  width: 350px;
  height: 350px;
  background: radial-gradient(circle, rgba(19, 194, 194, 0.15), transparent 70%);
  top: 40%;
  left: 40%;
  animation-delay: -14s;
}

.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(22, 119, 255, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(22, 119, 255, 0.03) 1px, transparent 1px);
  background-size: 60px 60px;
}

@keyframes float-shape {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(30px, -30px) scale(1.05); }
  66% { transform: translate(-20px, 20px) scale(0.95); }
}

/* ===== 左侧品牌面板 ===== */
.hero-panel {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px;
  opacity: 0;
  transform: translateX(-30px);
  transition: opacity 0.8s ease, transform 0.8s ease;
}

.hero-panel.visible {
  opacity: 1;
  transform: translateX(0);
}

.hero-content {
  max-width: 520px;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 16px;
  margin-bottom: 28px;
  border: 1px solid rgba(22, 119, 255, 0.2);
  border-radius: 20px;
  background: rgba(22, 119, 255, 0.06);
  font-size: 12px;
  font-weight: 500;
  color: #1677ff;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.badge-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #1677ff;
  animation: pulse-dot 2s ease-in-out infinite;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.5); }
}

.hero-title {
  margin: 0 0 20px;
  font-size: 44px;
  font-weight: 700;
  line-height: 1.2;
  color: #1a1a2e;
  letter-spacing: -0.02em;
}

.hero-title .highlight {
  background: linear-gradient(135deg, #1677ff 0%, #722ed1 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-desc {
  margin: 0 0 40px;
  font-size: 15px;
  line-height: 1.8;
  color: #666;
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 20px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 12px;
  background: #fff;
  opacity: 0;
  animation: stat-enter 0.5s ease forwards;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.stat-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
}

@keyframes stat-enter {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: #1677ff;
  letter-spacing: -0.01em;
}

.stat-label {
  font-size: 12px;
  color: #999;
}

/* ===== 右侧登录卡片 ===== */
.login-section {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  opacity: 0;
  transform: translateY(20px);
  transition: opacity 0.6s ease 0.15s, transform 0.6s ease 0.15s;
}

.login-section.visible {
  opacity: 1;
  transform: translateY(0);
}

.login-card {
  width: 100%;
  max-width: 400px;
  padding: 44px 40px;
  border-radius: 20px;
  background: #fff;
  box-shadow:
    0 2px 8px rgba(0, 0, 0, 0.04),
    0 12px 40px rgba(0, 0, 0, 0.06);
  transition: box-shadow 0.3s ease;
}

.login-card:hover {
  box-shadow:
    0 2px 8px rgba(0, 0, 0, 0.04),
    0 16px 48px rgba(0, 0, 0, 0.08);
}

.card-header {
  text-align: center;
  margin-bottom: 36px;
}

.card-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  margin-bottom: 16px;
  border-radius: 14px;
  background: linear-gradient(135deg, #e6f4ff 0%, #f0e6ff 100%);
  color: #1677ff;
}

.card-header h2 {
  margin: 0 0 6px;
  font-size: 24px;
  font-weight: 700;
  color: #1a1a2e;
  letter-spacing: -0.01em;
}

.card-header p {
  margin: 0;
  font-size: 14px;
  color: #999;
}

/* ===== 表单 ===== */
.login-form {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.input-label {
  font-size: 13px;
  font-weight: 500;
  color: #333;
}

.error-alert {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 8px;
  background: #fff2f0;
  border: 1px solid #ffccc7;
  color: #ff4d4f;
  font-size: 13px;
}

.error-fade-enter-active,
.error-fade-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.error-fade-enter-from,
.error-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.submit-btn {
  margin-top: 4px;
  height: 44px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.08em;
  transition: all 0.3s ease;
}

/* ===== 响应式 ===== */
@media (max-width: 1024px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .hero-panel {
    display: none;
  }

  .login-section {
    padding: 24px;
  }

  .login-card {
    padding: 36px 28px;
  }
}

@media (max-width: 480px) {
  .login-card {
    padding: 28px 20px;
    border-radius: 16px;
  }

  .card-header h2 {
    font-size: 20px;
  }
}
</style>
