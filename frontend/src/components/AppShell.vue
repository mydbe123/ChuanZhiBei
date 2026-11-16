<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'

const auth = useAuthStore()
const ui = useUiStore()
const route = useRoute()
const router = useRouter()

const NAV = [
  { name: 'dashboard', label: '学习工作台', icon: 'dashboard' },
  { name: 'tutor', label: 'AI 导师', icon: 'tutor' },
  { name: 'knowledge', label: '知识库', icon: 'knowledge' },
  { name: 'quiz', label: '智能测验', icon: 'quiz' },
  { name: 'plan', label: '学习计划', icon: 'plan' },
  { name: 'profile', label: '个人中心', icon: 'user' }
]

const isMobile = ref(window.innerWidth <= 900)
const mobileOpen = ref(false)
const pageTitle = computed(() => String(route.meta.title ?? '智学引擎'))

function onResize() {
  isMobile.value = window.innerWidth <= 900
  if (!isMobile.value) mobileOpen.value = false
}

function onUnauthorized() {
  auth.logout()
  router.replace({ name: 'login' })
}

function toggleSidebar() {
  if (isMobile.value) {
    mobileOpen.value = !mobileOpen.value
  } else {
    ui.toggleSidebar()
  }
}

function logout() {
  auth.logout()
  router.replace({ name: 'login' })
}

onMounted(() => {
  window.addEventListener('resize', onResize)
  window.addEventListener('zhixue:unauthorized', onUnauthorized)
  if (!auth.user) auth.loadMe()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  window.removeEventListener('zhixue:unauthorized', onUnauthorized)
})
</script>

<template>
  <div
    class="shell"
    :class="{ collapsed: ui.sidebarCollapsed && !isMobile, 'drawer-open': mobileOpen }"
  >
    <aside class="sidebar">
      <div class="brand">
        <div class="logo">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path
              d="M12 3l2.2 6.1L20.3 11l-6.1 2.2L12 19.3l-2.2-6.1L3.7 11l6.1-1.9z"
              fill="url(#logo-grad)"
            />
            <defs>
              <linearGradient id="logo-grad" x1="4" y1="4" x2="20" y2="20">
                <stop stop-color="#6366f1" />
                <stop offset="1" stop-color="#06b6d4" />
              </linearGradient>
            </defs>
          </svg>
        </div>
        <div class="brand-text">
          <strong>智学引擎</strong>
          <span>ZhiXue Engine</span>
        </div>
      </div>

      <nav class="nav">
        <RouterLink
          v-for="item in NAV"
          :key="item.name"
          :to="{ name: item.name }"
          class="nav-item"
          active-class="active"
          @click="mobileOpen = false"
        >
          <AppIcon :name="item.icon" :size="18" />
          <span class="nav-label">{{ item.label }}</span>
          <span class="nav-glow" />
        </RouterLink>
      </nav>

      <div class="side-foot">
        <div class="model-badge">
          <span class="dot" />
          <div>
            <strong>DeepSeek V4.1</strong>
            <span>Agent · RAG · 多模态</span>
          </div>
        </div>
      </div>
    </aside>

    <div v-if="mobileOpen" class="mask" @click="mobileOpen = false" />

    <div class="main">
      <header class="topbar">
        <button class="icon-btn" type="button" aria-label="切换侧边栏" @click="toggleSidebar">
          <AppIcon name="menu" :size="18" />
        </button>
        <div class="crumb">
          <span class="pulse-dot" />
          {{ pageTitle }}
        </div>
        <div class="spacer" />
        <button
          class="icon-btn"
          type="button"
          :aria-label="ui.theme === 'light' ? '切换深色模式' : '切换浅色模式'"
          @click="ui.toggleTheme()"
        >
          <AppIcon :name="ui.theme === 'light' ? 'moon' : 'sun'" :size="17" />
        </button>
        <RouterLink to="/profile" class="user-chip">
          <span class="avatar" :style="{ background: auth.user?.avatarColor ?? '#4f46e5' }">
            {{ auth.initial }}
          </span>
          <span class="user-name">{{ auth.user?.displayName ?? '同学' }}</span>
        </RouterLink>
        <button class="icon-btn" type="button" aria-label="退出登录" @click="logout">
          <AppIcon name="logout" :size="17" />
        </button>
      </header>

      <main class="content">
        <RouterView v-slot="{ Component }">
          <Transition name="page" mode="out-in">
            <component :is="Component" />
          </Transition>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  min-height: 100vh;
}

.sidebar {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: var(--sidebar-w);
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 18px 14px;
  background: var(--surface);
  border-right: 1px solid var(--border);
  z-index: 60;
  transition: width 0.24s cubic-bezier(0.22, 0.8, 0.3, 1), transform 0.24s ease;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px 16px;
}

.logo {
  width: 34px;
  height: 34px;
  border-radius: 11px;
  display: grid;
  place-items: center;
  background: var(--brand-50);
  flex: none;
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
  overflow: hidden;
  white-space: nowrap;
}

.brand-text strong {
  font-size: 14.5px;
  letter-spacing: 0.02em;
}

.brand-text span {
  font-size: 10px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-mute);
}

.nav {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  color: var(--text-soft);
  font-size: 13.5px;
  font-weight: 520;
  transition: background 0.18s ease, color 0.18s ease;
  overflow: hidden;
}

.nav-item:hover {
  background: var(--surface-soft);
  color: var(--text);
}

.nav-item.active {
  background: var(--brand-50);
  color: var(--brand-600);
  font-weight: 620;
}

.nav-glow {
  position: absolute;
  left: 0;
  top: 50%;
  width: 3px;
  height: 0;
  border-radius: 0 3px 3px 0;
  background: var(--brand-500);
  transform: translateY(-50%);
  transition: height 0.2s ease;
}

.nav-item.active .nav-glow {
  height: 18px;
}

.side-foot {
  margin-top: auto;
}

.model-badge {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
  border: 1px solid var(--border);
}

.model-badge .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--success-500);
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.16);
  flex: none;
}

.model-badge div {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
  overflow: hidden;
  white-space: nowrap;
}

.model-badge strong {
  font-size: 11.5px;
}

.model-badge span {
  font-size: 10px;
  color: var(--text-mute);
}

.main {
  flex: 1;
  margin-left: var(--sidebar-w);
  display: flex;
  flex-direction: column;
  min-width: 0;
  transition: margin-left 0.24s cubic-bezier(0.22, 0.8, 0.3, 1);
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 40;
  height: var(--topbar-h);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 22px;
  background: color-mix(in srgb, var(--bg) 82%, transparent);
  backdrop-filter: blur(14px);
  border-bottom: 1px solid var(--border);
}

.crumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-soft);
}

.pulse-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand-500);
  animation: pulse-dot 2.4s ease-in-out infinite;
}

.icon-btn {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-soft);
  cursor: pointer;
  transition: all 0.18s ease;
}

.icon-btn:hover {
  background: var(--surface);
  border-color: var(--border);
  color: var(--text);
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px 4px 4px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--text);
  transition: all 0.18s ease;
}

.user-chip:hover {
  border-color: var(--brand-300);
}

.avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.user-name {
  font-size: 12.5px;
  font-weight: 560;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.content {
  flex: 1;
  min-width: 0;
}

/* collapsed (desktop) */
.collapsed .sidebar {
  width: 74px;
}

.collapsed .main {
  margin-left: 74px;
}

.collapsed .brand-text,
.collapsed .nav-label,
.collapsed .model-badge div {
  display: none;
}

.collapsed .nav-item {
  justify-content: center;
  padding: 11px 0;
}

.collapsed .model-badge {
  justify-content: center;
}

/* mobile drawer */
.mask {
  position: fixed;
  inset: 0;
  background: rgba(10, 14, 26, 0.4);
  z-index: 55;
  animation: fade-in 0.2s ease both;
}

@media (max-width: 900px) {
  .sidebar {
    transform: translateX(-100%);
    box-shadow: var(--shadow-3);
  }
  .main {
    margin-left: 0;
  }
  .drawer-open .sidebar {
    transform: translateX(0);
  }
  .topbar {
    padding: 0 14px;
  }
  .user-name {
    display: none;
  }
}
</style>
