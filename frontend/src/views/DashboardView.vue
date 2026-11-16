<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import BarChart from '@/components/BarChart.vue'
import BaseButton from '@/components/BaseButton.vue'
import BaseCard from '@/components/BaseCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import ProgressRing from '@/components/ProgressRing.vue'
import { api } from '@/api/client'
import type { AnalyticsOverview } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/stores/toast'
import { formatMinutes, formatNumber, formatRelative } from '@/utils/format'

const auth = useAuthStore()
const router = useRouter()

const data = ref<AnalyticsOverview | null>(null)
const loading = ref(true)

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 11) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const weekly = computed(() =>
  (data.value?.weekly ?? []).map((item) => ({ label: item.label, value: item.minutes, hint: item.date }))
)

const mastery = computed(() => (data.value?.mastery ?? []).slice(0, 5))

const weakPoints = computed(() => data.value?.weakPoints ?? [])

const statCards = computed(() => {
  const stats = data.value?.stats
  return [
    { label: '近 30 天学习', value: formatMinutes(stats?.studyMinutes ?? 0), hint: `活跃 ${stats?.activeDays ?? 0} 天`, icon: 'clock' },
    { label: '连续打卡', value: `${stats?.streakDays ?? 0} 天`, hint: '保持节奏最有效', icon: 'fire' },
    { label: '测验平均分', value: `${stats?.avgScore ?? 0}`, hint: `共完成 ${stats?.quizzes ?? 0} 次`, icon: 'quiz' },
    { label: '知识库切片', value: formatNumber(stats?.chunks ?? 0), hint: `${stats?.documents ?? 0} 篇文档`, icon: 'knowledge' }
  ]
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    data.value = await api.get<AnalyticsOverview>('/analytics/overview')
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="hero fade-up">
      <div class="hero-main">
        <span class="eyebrow">
          <span class="dot" />
          {{ greeting }}，{{ auth.user?.displayName ?? '同学' }}
        </span>
        <h1>今天想学点什么？</h1>
        <p>
          {{ data?.insight ?? '正在读取你的学习画像…' }}
        </p>
        <div class="hero-actions">
          <BaseButton size="lg" @click="router.push({ name: 'tutor' })">
            <AppIcon name="tutor" :size="17" />
            去问 AI 导师
          </BaseButton>
          <BaseButton size="lg" variant="outline" @click="router.push({ name: 'quiz' })">
            <AppIcon name="quiz" :size="16" />
            生成一次测验
          </BaseButton>
        </div>
      </div>

      <div class="hero-ring">
        <ProgressRing
          :value="data?.stats.streakDays ? Math.min(100, data.stats.streakDays * 10) : 0"
          :size="132"
          label="学习势能"
        />
        <div class="ring-meta">
          <span>AI 调用 <strong>{{ data?.stats.aiCalls ?? 0 }}</strong> 次</span>
          <span>累计 tokens <strong>{{ formatNumber(data?.ai.tokens ?? 0) }}</strong></span>
        </div>
      </div>
    </div>

    <div class="grid grid-4 stats">
      <BaseCard v-for="card in statCards" :key="card.label" hover>
        <div class="stat">
          <span class="stat-icon"><AppIcon :name="card.icon" :size="17" /></span>
          <div>
            <span class="stat-label">{{ card.label }}</span>
            <strong>{{ loading ? '—' : card.value }}</strong>
            <span class="stat-hint">{{ card.hint }}</span>
          </div>
        </div>
      </BaseCard>
    </div>

    <div class="grid grid-2 main-grid">
      <BaseCard>
        <div class="card-head">
          <div>
            <h3>学习节奏</h3>
            <p class="muted">最近 7 天的学习时长分布</p>
          </div>
          <span class="chip chip-brand">{{ weekly.reduce((sum, d) => sum + d.value, 0) }} 分钟</span>
        </div>
        <div v-if="loading" class="skeleton chart-skeleton" />
        <BarChart v-else :data="weekly" :height="168" />
      </BaseCard>

      <BaseCard>
        <div class="card-head">
          <div>
            <h3>知识点掌握度</h3>
            <p class="muted">来自测验与练习的作答表现</p>
          </div>
          <span v-if="weakPoints.length" class="chip chip-warn">待加强 {{ weakPoints.length }}</span>
        </div>

        <div v-if="loading" class="skeleton list-skeleton" />
        <ul v-else-if="mastery.length" class="mastery">
          <li v-for="item in mastery" :key="item.point">
            <div class="mastery-top">
              <span class="point">{{ item.point }}</span>
              <span class="value" :class="{ low: item.mastery < 60 }">{{ item.mastery }}%</span>
            </div>
            <div class="bar">
              <span
                :style="{ width: `${item.mastery}%` }"
                :class="{ low: item.mastery < 60, mid: item.mastery >= 60 && item.mastery < 80 }"
              />
            </div>
            <span class="attempts">{{ item.attempts }} 次作答</span>
          </li>
        </ul>
        <EmptyState
          v-else
          title="还没有掌握度数据"
          description="完成一次 AI 测验，系统会自动分析你的知识点掌握情况"
        >
          <template #icon><AppIcon name="quiz" :size="22" /></template>
          <BaseButton size="sm" variant="soft" @click="router.push({ name: 'quiz' })">去测验</BaseButton>
        </EmptyState>
      </BaseCard>
    </div>

    <div class="grid grid-2">
      <BaseCard>
        <div class="card-head">
          <div>
            <h3>最近动态</h3>
            <p class="muted">你的学习轨迹</p>
          </div>
        </div>
        <ul v-if="(data?.recent.length ?? 0) > 0" class="timeline">
          <li v-for="(item, index) in data?.recent" :key="index">
            <span class="mark" :class="item.type.toLowerCase()" />
            <div class="tl-body">
              <strong>{{ item.title }}</strong>
              <span v-if="item.detail">{{ item.detail }}</span>
            </div>
            <span class="time">{{ formatRelative(item.at) }}</span>
          </li>
        </ul>
        <EmptyState v-else title="还没有学习记录" description="从向 AI 导师提一个问题开始吧">
          <template #icon><AppIcon name="tutor" :size="22" /></template>
        </EmptyState>
      </BaseCard>

      <BaseCard>
        <div class="card-head">
          <div>
            <h3>快捷入口</h3>
            <p class="muted">常见学习动作</p>
          </div>
        </div>
        <div class="quick">
          <button type="button" class="quick-item" @click="router.push({ name: 'tutor' })">
            <span class="q-icon"><AppIcon name="tutor" :size="18" /></span>
            <div>
              <strong>AI 导师答疑</strong>
              <span>流式讲解 · 工具调用 · 引用溯源</span>
            </div>
            <AppIcon name="arrow" :size="16" />
          </button>
          <button type="button" class="quick-item" @click="router.push({ name: 'knowledge' })">
            <span class="q-icon"><AppIcon name="knowledge" :size="18" /></span>
            <div>
              <strong>上传课程资料</strong>
              <span>PDF / Markdown / 文本 自动切片入库</span>
            </div>
            <AppIcon name="arrow" :size="16" />
          </button>
          <button type="button" class="quick-item" @click="router.push({ name: 'plan' })">
            <span class="q-icon"><AppIcon name="plan" :size="18" /></span>
            <div>
              <strong>生成学习计划</strong>
              <span>按天拆解目标，支持打卡追踪</span>
            </div>
            <AppIcon name="arrow" :size="16" />
          </button>
        </div>
      </BaseCard>
    </div>
  </div>
</template>

<style scoped>
.hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  padding: 30px 32px;
  border-radius: var(--radius-xl);
  border: 1px solid var(--border);
  background: linear-gradient(135deg, var(--surface) 0%, var(--brand-50) 120%);
  box-shadow: var(--shadow-1);
  overflow: hidden;
  margin-bottom: 18px;
}

.hero::after {
  content: '';
  position: absolute;
  width: 320px;
  height: 320px;
  right: -90px;
  top: -140px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.2), transparent 70%);
}

.hero-main {
  position: relative;
  z-index: 2;
  max-width: 620px;
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  letter-spacing: 0.06em;
  color: var(--text-soft);
  margin-bottom: 10px;
}

.eyebrow .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--success-500);
  box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.14);
}

h1 {
  font-size: 27px;
  letter-spacing: -0.025em;
  margin-bottom: 10px;
}

.hero p {
  font-size: 13.5px;
  line-height: 1.8;
  color: var(--text-soft);
  max-width: 540px;
}

.hero-actions {
  display: flex;
  gap: 10px;
  margin-top: 20px;
  flex-wrap: wrap;
}

.hero-ring {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.ring-meta {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  font-size: 11.5px;
  color: var(--text-mute);
}

.ring-meta strong {
  color: var(--text);
  font-weight: 650;
}

.stats {
  margin-bottom: 18px;
}

.stat {
  display: flex;
  gap: 13px;
  align-items: flex-start;
}

.stat-icon {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: var(--brand-50);
  color: var(--brand-500);
  flex: none;
}

.stat-label {
  font-size: 11.5px;
  color: var(--text-mute);
  letter-spacing: 0.04em;
}

.stat strong {
  display: block;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.35;
}

.stat-hint {
  font-size: 11.5px;
  color: var(--text-mute);
}

.main-grid {
  margin-bottom: 18px;
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.card-head h3 {
  font-size: 15px;
}

.card-head p {
  font-size: 12px;
  margin-top: 3px;
}

.chart-skeleton {
  height: 168px;
  border-radius: var(--radius-md);
}

.list-skeleton {
  height: 168px;
  border-radius: var(--radius-md);
}

.mastery {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 13px;
}

.mastery-top {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  font-size: 12.5px;
}

.point {
  font-weight: 560;
}

.value {
  font-weight: 700;
  color: var(--success-500);
  font-variant-numeric: tabular-nums;
}

.value.low {
  color: var(--warn-500);
}

.bar {
  margin-top: 6px;
  height: 7px;
  border-radius: 999px;
  background: var(--surface-mute);
  overflow: hidden;
}

.bar span {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #34d399, #10b981);
  transition: width 0.7s cubic-bezier(0.22, 0.8, 0.3, 1);
}

.bar span.low {
  background: linear-gradient(90deg, #fbbf24, #f59e0b);
}

.bar span.mid {
  background: linear-gradient(90deg, #60a5fa, #6366f1);
}

.attempts {
  font-size: 11px;
  color: var(--text-mute);
}

.timeline {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.timeline li {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--border);
}

.timeline li:last-child {
  border-bottom: none;
}

.mark {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 6px;
  background: var(--brand-500);
  flex: none;
}

.mark.quiz {
  background: var(--accent-500);
}

.mark.plan_task {
  background: var(--success-500);
}

.mark.chat {
  background: var(--brand-500);
}

.tl-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.tl-body strong {
  font-size: 12.8px;
  font-weight: 560;
}

.tl-body span {
  font-size: 12px;
  color: var(--text-mute);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time {
  font-size: 11px;
  color: var(--text-mute);
  white-space: nowrap;
}

.quick {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.quick-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
  background: var(--surface-soft);
  cursor: pointer;
  text-align: left;
  transition: all 0.2s ease;
  color: var(--text-mute);
}

.quick-item:hover {
  border-color: var(--brand-300);
  background: var(--brand-50);
  transform: translateX(2px);
}

.q-icon {
  width: 36px;
  height: 36px;
  border-radius: 11px;
  display: grid;
  place-items: center;
  background: var(--surface);
  color: var(--brand-500);
  flex: none;
  box-shadow: var(--shadow-1);
}

.quick-item div {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.quick-item strong {
  font-size: 13px;
  color: var(--text);
}

.quick-item span {
  font-size: 11.5px;
  color: var(--text-mute);
}

@media (max-width: 860px) {
  .hero {
    flex-direction: column;
    align-items: flex-start;
    padding: 22px;
  }
  .hero-ring {
    align-self: center;
  }
  h1 {
    font-size: 22px;
  }
}
</style>
