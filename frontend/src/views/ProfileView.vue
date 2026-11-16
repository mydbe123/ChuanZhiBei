<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import BaseButton from '@/components/BaseButton.vue'
import BaseCard from '@/components/BaseCard.vue'
import { api } from '@/api/client'
import type { AiUsageItem } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/stores/toast'
import { useUiStore } from '@/stores/ui'
import { formatDate, formatNumber, formatRelative } from '@/utils/format'

const auth = useAuthStore()
const ui = useUiStore()
const router = useRouter()

const PALETTE = ['#4f46e5', '#0ea5e9', '#10b981', '#f59e0b', '#ec4899', '#8b5cf6']

const form = reactive({
  displayName: '',
  email: '',
  school: '',
  grade: '',
  major: '',
  learningGoal: '',
  level: 'BEGINNER',
  avatarColor: '#4f46e5'
})

const saving = ref(false)
const usage = ref<AiUsageItem[]>([])
const loadingUsage = ref(true)

const LEVEL_LABEL: Record<string, string> = {
  BEGINNER: '零基础',
  INTERMEDIATE: '有基础',
  ADVANCED: '进阶'
}

const SCENE_LABEL: Record<string, string> = {
  CHAT: 'AI 对话',
  QUIZ_GENERATE: 'AI 出题',
  QUIZ_DIAGNOSIS: '学情诊断',
  PLAN_GENERATE: '学习计划',
  guard: '安全拦截'
}

const tokenTotal = computed(() =>
  usage.value.reduce((sum, item) => sum + item.promptTokens + item.completionTokens, 0)
)
const avgLatency = computed(() => {
  if (!usage.value.length) return 0
  return Math.round(usage.value.reduce((sum, item) => sum + item.latencyMs, 0) / usage.value.length)
})

onMounted(async () => {
  await auth.loadMe()
  syncForm()
  await loadUsage()
})

function syncForm() {
  const user = auth.user
  if (!user) return
  form.displayName = user.displayName ?? ''
  form.email = user.email ?? ''
  form.school = user.school ?? ''
  form.grade = user.grade ?? ''
  form.major = user.major ?? ''
  form.learningGoal = user.learningGoal ?? ''
  form.level = user.level ?? 'BEGINNER'
  form.avatarColor = user.avatarColor ?? '#4f46e5'
}

async function save() {
  if (!form.displayName.trim()) {
    toast.error('昵称不能为空')
    return
  }
  saving.value = true
  try {
    await auth.updateProfile({ ...form, displayName: form.displayName.trim() })
    toast.success('资料已保存，AI 导师会用新的画像来回答')
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

async function loadUsage() {
  loadingUsage.value = true
  try {
    usage.value = await api.get<AiUsageItem[]>('/analytics/ai-usage')
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    loadingUsage.value = false
  }
}

function logout() {
  auth.logout()
  router.replace({ name: 'login' })
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">
          <AppIcon name="user" :size="22" />
          个人中心
        </h1>
        <p class="page-sub">完善学习画像，AI 导师会据此调整讲解深度、举例方式与练习难度。</p>
      </div>
    </div>

    <div class="layout">
      <div class="col-left">
        <BaseCard class="identity">
          <div class="avatar" :style="{ background: form.avatarColor }">
            {{ (auth.user?.displayName ?? '学').slice(0, 1) }}
          </div>
          <h2>{{ auth.user?.displayName ?? '同学' }}</h2>
          <p class="username">@{{ auth.user?.username }}</p>
          <div class="tags">
            <span class="chip chip-brand">{{ LEVEL_LABEL[auth.user?.level ?? 'BEGINNER'] }}</span>
            <span v-if="auth.user?.major" class="chip">{{ auth.user.major }}</span>
          </div>
          <div class="divider" />
          <dl>
            <div>
              <dt>学校</dt>
              <dd>{{ auth.user?.school || '未填写' }}</dd>
            </div>
            <div>
              <dt>年级</dt>
              <dd>{{ auth.user?.grade || '未填写' }}</dd>
            </div>
            <div>
              <dt>加入时间</dt>
              <dd>{{ formatDate(auth.user?.createdAt) }}</dd>
            </div>
          </dl>
        </BaseCard>

        <BaseCard>
          <h3>外观与账号</h3>
          <div class="setting-row">
            <div>
              <strong>深色模式</strong>
              <span class="muted">跟随你的阅读习惯</span>
            </div>
            <button class="theme-toggle" type="button" @click="ui.toggleTheme()">
              <AppIcon :name="ui.theme === 'light' ? 'moon' : 'sun'" :size="16" />
              {{ ui.theme === 'light' ? '切换到深色' : '切换到浅色' }}
            </button>
          </div>
          <div class="setting-row">
            <div>
              <strong>退出登录</strong>
              <span class="muted">本机 token 将被清除</span>
            </div>
            <BaseButton variant="danger" size="sm" @click="logout">
              <AppIcon name="logout" :size="14" />
              退出
            </BaseButton>
          </div>
        </BaseCard>
      </div>

      <div class="col-right">
        <BaseCard>
          <h3>学习画像</h3>
          <p class="muted sub">这些信息会注入 AI 的系统提示词，帮助导师"因材施教"</p>

          <div class="form-grid">
            <label class="field">
              <span>昵称</span>
              <input v-model="form.displayName" placeholder="如何称呼你" />
            </label>
            <label class="field">
              <span>邮箱</span>
              <input v-model="form.email" placeholder="选填" />
            </label>
            <label class="field">
              <span>学校</span>
              <input v-model="form.school" placeholder="所在院校" />
            </label>
            <label class="field">
              <span>年级</span>
              <input v-model="form.grade" placeholder="如 大二" />
            </label>
            <label class="field">
              <span>专业</span>
              <input v-model="form.major" placeholder="如 计算机科学与技术" />
            </label>
            <label class="field">
              <span>当前水平</span>
              <select v-model="form.level">
                <option value="BEGINNER">零基础 / 初学</option>
                <option value="INTERMEDIATE">有一定基础</option>
                <option value="ADVANCED">进阶提升</option>
              </select>
            </label>
          </div>

          <label class="field">
            <span>学习目标</span>
            <textarea
              v-model="form.learningGoal"
              rows="3"
              placeholder="例如：两个月内系统掌握数据结构与算法，冲击蓝桥杯省一"
            />
          </label>

          <div class="colors">
            <span class="label">头像颜色</span>
            <button
              v-for="color in PALETTE"
              :key="color"
              type="button"
              class="swatch"
              :class="{ active: form.avatarColor === color }"
              :style="{ background: color }"
              @click="form.avatarColor = color"
            />
          </div>

          <div class="actions">
            <BaseButton :loading="saving" @click="save">
              <AppIcon name="check" :size="15" />
              保存资料
            </BaseButton>
            <BaseButton variant="ghost" @click="syncForm">重置</BaseButton>
          </div>
        </BaseCard>

        <BaseCard>
          <div class="ai-head">
            <div>
              <h3>AI 调用记录</h3>
              <p class="muted sub">透明可追溯：记录场景、模型、token 与耗时</p>
            </div>
            <div class="ai-stats">
              <span><strong>{{ usage.length }}</strong> 次</span>
              <span><strong>{{ formatNumber(tokenTotal) }}</strong> tokens</span>
              <span><strong>{{ avgLatency }}</strong> ms 平均</span>
            </div>
          </div>

          <div v-if="loadingUsage" class="skeleton" style="height: 120px" />
          <div v-else-if="usage.length" class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>场景</th>
                  <th>模型</th>
                  <th>Tokens</th>
                  <th>耗时</th>
                  <th>状态</th>
                  <th>时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in usage" :key="item.id">
                  <td>{{ SCENE_LABEL[item.scene] ?? item.scene }}</td>
                  <td class="mono">{{ item.model }}</td>
                  <td>{{ formatNumber(item.promptTokens + item.completionTokens) }}</td>
                  <td>{{ item.latencyMs }} ms</td>
                  <td>
                    <span class="chip" :class="item.status === 'OK' ? 'chip-success' : 'chip-danger'">
                      {{ item.status }}
                    </span>
                  </td>
                  <td class="muted">{{ formatRelative(item.createdAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else class="muted empty">还没有 AI 调用记录，去和 AI 导师聊两句吧</p>
        </BaseCard>
      </div>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 16px;
  align-items: start;
}

.col-left,
.col-right {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.identity {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 6px;
}

.avatar {
  width: 74px;
  height: 74px;
  border-radius: 24px;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 28px;
  font-weight: 700;
  box-shadow: var(--shadow-2);
  margin-bottom: 6px;
}

.identity h2 {
  font-size: 17px;
}

.username {
  font-size: 12px;
  color: var(--text-mute);
}

.tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: center;
  margin-top: 6px;
}

.divider {
  width: 100%;
  height: 1px;
  background: var(--border);
  margin: 14px 0 10px;
}

dl {
  width: 100%;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

dl div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 12.5px;
}

dt {
  color: var(--text-mute);
}

dd {
  margin: 0;
  font-weight: 520;
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

h3 {
  font-size: 15px;
}

.sub {
  font-size: 12px;
  margin: 4px 0 16px;
}

.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 0;
  border-bottom: 1px dashed var(--border);
}

.setting-row:last-child {
  border-bottom: none;
}

.setting-row div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.setting-row strong {
  font-size: 13px;
  font-weight: 560;
}

.setting-row span {
  font-size: 11.5px;
}

.theme-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid var(--border-strong);
  background: var(--surface-soft);
  font-size: 12px;
  color: var(--text-soft);
  cursor: pointer;
  transition: all 0.18s ease;
}

.theme-toggle:hover {
  border-color: var(--brand-300);
  color: var(--brand-600);
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
}

.field span {
  font-size: 12px;
  font-weight: 550;
  color: var(--text-soft);
}

.field input,
.field select,
.field textarea {
  padding: 10px 13px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-strong);
  background: var(--surface-soft);
  resize: vertical;
}

.field input:focus,
.field select:focus,
.field textarea:focus {
  outline: none;
  border-color: var(--brand-300);
  box-shadow: var(--ring);
  background: var(--surface);
}

.colors {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 18px;
}

.label {
  font-size: 12px;
  color: var(--text-soft);
  margin-right: 4px;
}

.swatch {
  width: 24px;
  height: 24px;
  border-radius: 8px;
  border: 2px solid transparent;
  cursor: pointer;
  transition: transform 0.18s ease;
}

.swatch:hover {
  transform: scale(1.08);
}

.swatch.active {
  border-color: var(--text);
  box-shadow: 0 0 0 2px var(--surface) inset;
}

.actions {
  display: flex;
  gap: 10px;
}

.ai-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
}

.ai-stats {
  display: flex;
  gap: 16px;
  font-size: 11.5px;
  color: var(--text-mute);
}

.ai-stats strong {
  color: var(--text);
  font-size: 14px;
  margin-right: 3px;
}

.table-wrap {
  margin-top: 14px;
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.3px;
}

th {
  text-align: left;
  font-weight: 560;
  color: var(--text-mute);
  padding: 8px 10px;
  border-bottom: 1px solid var(--border);
  white-space: nowrap;
}

td {
  padding: 9px 10px;
  border-bottom: 1px dashed var(--border);
  white-space: nowrap;
}

tbody tr:hover {
  background: var(--surface-soft);
}

.mono {
  font-family: var(--font-mono);
  font-size: 11.5px;
  color: var(--text-soft);
}

.empty {
  margin-top: 16px;
  font-size: 12.5px;
  text-align: center;
  padding: 20px 0;
}

@media (max-width: 960px) {
  .layout {
    grid-template-columns: 1fr;
  }
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
