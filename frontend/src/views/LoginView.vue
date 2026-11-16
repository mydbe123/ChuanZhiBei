<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BaseButton from '@/components/BaseButton.vue'
import AppIcon from '@/components/AppIcon.vue'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/stores/toast'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const mode = ref<'login' | 'register'>('login')
const busy = ref(false)

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({
  username: '',
  password: '',
  displayName: '',
  school: '',
  grade: '',
  major: '',
  learningGoal: '',
  level: 'BEGINNER'
})

const HIGHLIGHTS = [
  { icon: 'tutor', title: 'AI Agent 导师', desc: '会规划、会调用工具、会追问的苏格拉底式导师' },
  { icon: 'knowledge', title: 'RAG 知识增强', desc: '上传讲义即可问答，答案带原文出处' },
  { icon: 'spark', title: '多模态解题', desc: '拍照上传题目，图文联合理解与讲解' },
  { icon: 'shield', title: '可控的 AI', desc: '内容护栏、提示词注入防护与全链路审计' }
]

async function submit() {
  if (busy.value) return
  busy.value = true
  try {
    if (mode.value === 'login') {
      if (!loginForm.username || !loginForm.password) {
        toast.error('请输入用户名和密码')
        return
      }
      await auth.login(loginForm.username.trim(), loginForm.password)
      toast.success('欢迎回来！')
    } else {
      if (!registerForm.username || !registerForm.password || !registerForm.displayName) {
        toast.error('请填写用户名、密码与昵称')
        return
      }
      if (registerForm.username.trim().length < 3) {
        toast.error('用户名至少 3 个字符')
        return
      }
      if (registerForm.password.length < 6) {
        toast.error('密码至少 6 位')
        return
      }
      await auth.register({ ...registerForm, username: registerForm.username.trim() })
      toast.success('注册成功，开始你的 AI 学习之旅')
    }
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.replace(redirect)
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    busy.value = false
  }
}

async function demo() {
  if (busy.value) return
  busy.value = true
  try {
    await auth.demoLogin()
    toast.success('已进入体验账号')
    router.replace('/dashboard')
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="auth">
    <section class="intro">
      <div class="intro-inner">
        <div class="brand">
          <div class="logo">
            <svg width="26" height="26" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M12 3l2.2 6.1L20.3 11l-6.1 2.2L12 19.3l-2.2-6.1L3.7 11l6.1-1.9z" fill="#fff" />
            </svg>
          </div>
          <div>
            <strong>智学引擎</strong>
            <span>ZhiXue Engine</span>
          </div>
        </div>

        <h1>
          让每一次学习<br />
          都有 AI 陪跑
        </h1>
        <p class="lede">
          以大模型 Agent 为核心的学习平台：诊断你的薄弱点、生成专属计划、出题、批改并持续追踪掌握度。
        </p>

        <ul class="highlights">
          <li v-for="item in HIGHLIGHTS" :key="item.title">
            <span class="glyph"><AppIcon :name="item.icon" :size="17" /></span>
            <div>
              <strong>{{ item.title }}</strong>
              <span>{{ item.desc }}</span>
            </div>
          </li>
        </ul>

        <div class="footnote">
          <span class="dot" />
          DeepSeek V4.1 · Agent + RAG + 多模态
        </div>
      </div>
      <div class="glow one" />
      <div class="glow two" />
    </section>

    <section class="form-side">
      <div class="form-card">
        <div class="tabs">
          <button
            type="button"
            :class="{ active: mode === 'login' }"
            @click="mode = 'login'"
          >
            登录
          </button>
          <button
            type="button"
            :class="{ active: mode === 'register' }"
            @click="mode = 'register'"
          >
            注册
          </button>
          <span class="indicator" :class="{ right: mode === 'register' }" />
        </div>

        <form @submit.prevent="submit">
          <template v-if="mode === 'login'">
            <label class="field">
              <span>用户名</span>
              <input v-model="loginForm.username" autocomplete="username" placeholder="请输入用户名" />
            </label>
            <label class="field">
              <span>密码</span>
              <input
                v-model="loginForm.password"
                type="password"
                autocomplete="current-password"
                placeholder="请输入密码"
              />
            </label>
          </template>

          <template v-else>
            <div class="row-2">
              <label class="field">
                <span>用户名</span>
                <input v-model="registerForm.username" autocomplete="username" placeholder="3-32 个字符" />
              </label>
              <label class="field">
                <span>昵称</span>
                <input v-model="registerForm.displayName" placeholder="如何称呼你" />
              </label>
            </div>
            <label class="field">
              <span>密码</span>
              <input
                v-model="registerForm.password"
                type="password"
                autocomplete="new-password"
                placeholder="至少 6 位"
              />
            </label>
            <div class="row-2">
              <label class="field">
                <span>学校</span>
                <input v-model="registerForm.school" placeholder="选填" />
              </label>
              <label class="field">
                <span>年级</span>
                <input v-model="registerForm.grade" placeholder="如 大二" />
              </label>
            </div>
            <div class="row-2">
              <label class="field">
                <span>专业</span>
                <input v-model="registerForm.major" placeholder="如 计算机科学与技术" />
              </label>
              <label class="field">
                <span>当前水平</span>
                <select v-model="registerForm.level">
                  <option value="BEGINNER">零基础 / 初学</option>
                  <option value="INTERMEDIATE">有一定基础</option>
                  <option value="ADVANCED">进阶提升</option>
                </select>
              </label>
            </div>
            <label class="field">
              <span>学习目标</span>
              <input v-model="registerForm.learningGoal" placeholder="例如：两个月掌握数据结构与算法" />
            </label>
          </template>

          <BaseButton type="submit" block size="lg" :loading="busy">
            {{ mode === 'login' ? '登录' : '创建账号' }}
          </BaseButton>
        </form>

        <div class="divider"><span>或</span></div>

        <BaseButton variant="outline" block :loading="busy" @click="demo">
          <AppIcon name="spark" :size="16" />
          一键体验（评审推荐）
        </BaseButton>

        <p class="tip">
          演示账号 <code>demo / demo123456</code> · AI 输出仅供学习参考
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
}

.intro {
  position: relative;
  overflow: hidden;
  background: linear-gradient(150deg, #1b1a44 0%, #2c2a6f 45%, #0e7490 100%);
  color: #fff;
  display: flex;
  align-items: center;
  padding: 56px clamp(28px, 6vw, 84px);
}

.intro-inner {
  position: relative;
  z-index: 2;
  max-width: 520px;
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
}

.brand .logo {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.22);
}

.brand div {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.brand strong {
  font-size: 15px;
  letter-spacing: 0.02em;
}

.brand span {
  font-size: 10px;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  opacity: 0.66;
}

h1 {
  font-size: clamp(30px, 3.6vw, 42px);
  line-height: 1.24;
  font-weight: 750;
  letter-spacing: -0.03em;
  color: #fff;
}

.lede {
  font-size: 14.5px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.76);
  max-width: 460px;
}

.highlights {
  list-style: none;
  padding: 0;
  margin: 6px 0 0;
  display: grid;
  gap: 13px;
}

.highlights li {
  display: flex;
  gap: 11px;
  align-items: flex-start;
}

.glyph {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.16);
  flex: none;
}

.highlights strong {
  display: block;
  font-size: 13.5px;
  font-weight: 620;
}

.highlights span {
  font-size: 12.5px;
  color: rgba(255, 255, 255, 0.66);
  line-height: 1.6;
}

.footnote {
  margin-top: 8px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 11.5px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.6);
}

.footnote .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #34d399;
  box-shadow: 0 0 0 4px rgba(52, 211, 153, 0.18);
}

.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.5;
}

.glow.one {
  width: 420px;
  height: 420px;
  background: rgba(99, 102, 241, 0.55);
  top: -140px;
  right: -120px;
}

.glow.two {
  width: 340px;
  height: 340px;
  background: rgba(6, 182, 212, 0.4);
  bottom: -140px;
  left: -80px;
}

.form-side {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 20px;
}

.form-card {
  width: 100%;
  max-width: 420px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-3);
  padding: 28px;
  animation: fade-up 0.4s cubic-bezier(0.22, 0.8, 0.3, 1) both;
}

.tabs {
  position: relative;
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: var(--surface-mute);
  border-radius: 999px;
  padding: 4px;
  margin-bottom: 22px;
}

.tabs button {
  position: relative;
  z-index: 2;
  height: 34px;
  border: none;
  background: transparent;
  border-radius: 999px;
  font-size: 13.5px;
  font-weight: 560;
  color: var(--text-soft);
  cursor: pointer;
  transition: color 0.2s ease;
}

.tabs button.active {
  color: var(--brand-600);
}

.indicator {
  position: absolute;
  top: 4px;
  left: 4px;
  width: calc(50% - 4px);
  height: 34px;
  border-radius: 999px;
  background: var(--surface);
  box-shadow: var(--shadow-1);
  transition: transform 0.26s cubic-bezier(0.22, 0.8, 0.3, 1);
}

.indicator.right {
  transform: translateX(100%);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
}

.field span {
  font-size: 12px;
  font-weight: 560;
  color: var(--text-soft);
}

.field input,
.field select {
  height: 42px;
  padding: 0 13px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-strong);
  background: var(--surface-soft);
  transition: border-color 0.18s ease, background 0.18s ease, box-shadow 0.18s ease;
}

.field input:focus,
.field select:focus {
  outline: none;
  border-color: var(--brand-300);
  background: var(--surface);
  box-shadow: var(--ring);
}

.row-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.divider {
  position: relative;
  text-align: center;
  margin: 18px 0 14px;
}

.divider::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 0;
  right: 0;
  height: 1px;
  background: var(--border);
}

.divider span {
  position: relative;
  padding: 0 12px;
  background: var(--surface);
  font-size: 11.5px;
  color: var(--text-mute);
}

.tip {
  margin-top: 16px;
  text-align: center;
  font-size: 11.5px;
  color: var(--text-mute);
  line-height: 1.7;
}

.tip code {
  font-family: var(--font-mono);
  background: var(--surface-mute);
  padding: 1px 6px;
  border-radius: 5px;
}

@media (max-width: 940px) {
  .auth {
    grid-template-columns: 1fr;
  }
  .intro {
    padding: 38px 24px 30px;
  }
  .highlights,
  .footnote {
    display: none;
  }
  h1 {
    font-size: 27px;
  }
  .form-side {
    padding: 20px 16px 40px;
  }
  .form-card {
    box-shadow: var(--shadow-2);
  }
}
</style>
