<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import BaseButton from '@/components/BaseButton.vue'
import BaseCard from '@/components/BaseCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import ProgressRing from '@/components/ProgressRing.vue'
import { api } from '@/api/client'
import type { PlanDetail, PlanSummary, StudyTaskView } from '@/api/types'
import { toast } from '@/stores/toast'
import { formatMinutes, formatRelative } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const mode = ref<'list' | 'detail'>('list')
const plans = ref<PlanSummary[]>([])
const detail = ref<PlanDetail | null>(null)
const loading = ref(true)
const generating = ref(false)
const toggling = ref<number | null>(null)

const form = reactive({
  goal: '',
  level: 'BEGINNER',
  days: 7,
  dailyMinutes: 60
})

const LEVEL_LABEL: Record<string, string> = {
  BEGINNER: '零基础',
  INTERMEDIATE: '有基础',
  ADVANCED: '进阶'
}

const doneTasks = computed(() => detail.value?.plan.doneTasks ?? 0)
const totalTasks = computed(() => detail.value?.plan.totalTasks ?? 0)
const totalMinutes = computed(
  () => detail.value?.days.reduce((sum, day) => sum + day.totalMinutes, 0) ?? 0
)

onMounted(async () => {
  await loadList()
  const id = route.query.id
  if (typeof id === 'string' && id) {
    await openPlan(Number(id))
  }
})

async function loadList() {
  loading.value = true
  try {
    plans.value = await api.get<PlanSummary[]>('/plan')
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function generate() {
  if (generating.value) return
  generating.value = true
  try {
    const created = await api.post<PlanDetail>('/plan/generate', {
      goal: form.goal.trim() || '系统掌握数据结构与算法核心知识',
      level: form.level,
      days: form.days,
      dailyMinutes: form.dailyMinutes
    })
    toast.success('学习计划已生成')
    await loadList()
    detail.value = created
    mode.value = 'detail'
    router.replace({ name: 'plan', query: { id: String(created.plan.id) } })
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    generating.value = false
  }
}

async function openPlan(id: number) {
  if (!id || Number.isNaN(id)) return
  try {
    detail.value = await api.get<PlanDetail>(`/plan/${id}`)
    mode.value = 'detail'
    if (route.query.id !== String(id)) {
      router.replace({ name: 'plan', query: { id: String(id) } })
    }
  } catch (e) {
    toast.error((e as Error).message)
  }
}

function backToList() {
  mode.value = 'list'
  detail.value = null
  router.replace({ name: 'plan' })
  void loadList()
}

async function toggleTask(task: StudyTaskView) {
  if (toggling.value) return
  toggling.value = task.id
  try {
    detail.value = await api.patch<PlanDetail>(`/plan/tasks/${task.id}`, {
      status: task.status === 'DONE' ? 'TODO' : 'DONE'
    })
    if (task.status !== 'DONE') toast.success('打卡成功，继续保持！')
    await loadList()
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    toggling.value = null
  }
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">
          <AppIcon name="plan" :size="22" />
          学习计划
        </h1>
        <p class="page-sub">
          把模糊的目标交给 AI，拆成每天可执行、可打卡的任务；完成情况会自动计入学习统计。
        </p>
      </div>
      <BaseButton v-if="mode === 'detail'" variant="outline" @click="backToList">
        <AppIcon name="arrow" :size="15" />
        返回列表
      </BaseButton>
    </div>

    <template v-if="mode === 'list'">
      <BaseCard class="creator">
        <div class="creator-grid">
          <label class="field grow">
            <span>学习目标</span>
            <input
              v-model="form.goal"
              placeholder="例如：两周内掌握二叉树与图的基础算法"
              @keydown.enter="generate"
            />
          </label>
          <label class="field">
            <span>当前水平</span>
            <select v-model="form.level">
              <option value="BEGINNER">零基础</option>
              <option value="INTERMEDIATE">有基础</option>
              <option value="ADVANCED">进阶</option>
            </select>
          </label>
          <label class="field narrow">
            <span>计划天数</span>
            <input v-model.number="form.days" type="number" min="3" max="30" />
          </label>
          <label class="field narrow">
            <span>每天分钟</span>
            <input v-model.number="form.dailyMinutes" type="number" min="20" max="300" step="10" />
          </label>
          <BaseButton size="lg" :loading="generating" @click="generate">
            <AppIcon name="spark" :size="16" />
            {{ generating ? 'AI 正在规划…' : '生成计划' }}
          </BaseButton>
        </div>
        <p class="creator-hint">Agent 会调用学习计划工具，按天拆解任务并给出验收标准，通常需要 10-30 秒。</p>
      </BaseCard>

      <div v-if="loading" class="grid grid-3">
        <div v-for="i in 3" :key="i" class="skeleton" style="height: 148px; border-radius: 20px" />
      </div>

      <div v-else-if="plans.length" class="grid grid-3">
        <BaseCard v-for="plan in plans" :key="plan.id" hover class="plan-card">
          <div class="plan-top">
            <span class="chip chip-brand">{{ LEVEL_LABEL[plan.level] ?? plan.level }}</span>
            <span class="muted">{{ plan.durationDays }} 天</span>
          </div>
          <h3>{{ plan.title }}</h3>
          <p class="goal">{{ plan.goal }}</p>
          <div class="bar">
            <span :style="{ width: `${plan.progress}%` }" />
          </div>
          <div class="plan-foot">
            <span class="muted">
              {{ plan.doneTasks }}/{{ plan.totalTasks }} 个任务 · {{ plan.progress }}%
            </span>
            <BaseButton size="sm" variant="soft" @click="openPlan(plan.id)">查看</BaseButton>
          </div>
          <span class="created">{{ formatRelative(plan.createdAt) }}</span>
        </BaseCard>
      </div>

      <BaseCard v-else>
        <EmptyState
          title="还没有学习计划"
          description="写下一个目标，AI 会把它拆成每天可完成的小任务，并给出验收标准"
        >
          <template #icon><AppIcon name="plan" :size="22" /></template>
        </EmptyState>
      </BaseCard>
    </template>

    <template v-else-if="mode === 'detail' && detail">
      <BaseCard class="summary">
        <div class="summary-main">
          <span class="chip chip-brand">{{ LEVEL_LABEL[detail.plan.level] ?? detail.plan.level }}</span>
          <h2>{{ detail.plan.title }}</h2>
          <p class="goal-line">{{ detail.plan.goal }}</p>
          <p v-if="detail.plan.summary" class="summary-text">{{ detail.plan.summary }}</p>
          <div class="summary-stats">
            <span><strong>{{ detail.days.length }}</strong> 天</span>
            <span><strong>{{ totalTasks }}</strong> 个任务</span>
            <span><strong>{{ formatMinutes(totalMinutes) }}</strong> 预计投入</span>
            <span><strong>{{ detail.plan.dailyMinutes }}</strong> 分钟 / 天</span>
          </div>
        </div>
        <ProgressRing :value="detail.plan.progress" :size="120" label="完成度" />
      </BaseCard>

      <div class="days">
        <section v-for="day in detail.days" :key="day.day" class="day">
          <header class="day-head">
            <span class="day-badge">Day {{ day.day }}</span>
            <span class="day-meta">{{ day.tasks.length }} 个任务 · {{ day.totalMinutes }} 分钟</span>
          </header>
          <div class="task-list">
            <article
              v-for="task in day.tasks"
              :key="task.id"
              class="task"
              :class="{ done: task.status === 'DONE' }"
            >
              <button
                class="check"
                type="button"
                :disabled="toggling === task.id"
                :aria-label="task.status === 'DONE' ? '取消打卡' : '完成任务'"
                @click="toggleTask(task)"
              >
                <AppIcon v-if="task.status === 'DONE'" name="check" :size="13" />
              </button>
              <div class="task-body">
                <div class="task-title">
                  <strong>{{ task.title }}</strong>
                  <span class="minutes">{{ task.minutes }} 分钟</span>
                </div>
                <p v-if="task.detail">{{ task.detail }}</p>
                <div v-if="task.knowledgePoints.length" class="points">
                  <span v-for="point in task.knowledgePoints" :key="point" class="chip">{{ point }}</span>
                </div>
              </div>
            </article>
          </div>
        </section>
      </div>

      <div class="foot-bar">
        <span class="muted">已完成 {{ doneTasks }} / {{ totalTasks }} 个任务</span>
        <BaseButton variant="outline" @click="router.push({ name: 'tutor' })">
          <AppIcon name="tutor" :size="15" />
          遇到卡点，问问 AI 导师
        </BaseButton>
      </div>
    </template>
  </div>
</template>

<style scoped>
.creator {
  margin-bottom: 18px;
}

.creator-grid {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  flex-wrap: wrap;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 120px;
}

.field.grow {
  flex: 1;
  min-width: 260px;
}

.field.narrow {
  width: 104px;
  min-width: 104px;
}

.field span {
  font-size: 11.5px;
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
}

.field input:focus,
.field select:focus {
  outline: none;
  border-color: var(--brand-300);
  box-shadow: var(--ring);
  background: var(--surface);
}

.creator-hint {
  margin-top: 12px;
  font-size: 11.5px;
  color: var(--text-mute);
}

.plan-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.plan-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11.5px;
}

.plan-card h3 {
  font-size: 14.5px;
  line-height: 1.45;
}

.goal {
  font-size: 12px;
  color: var(--text-mute);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.bar {
  height: 6px;
  border-radius: 999px;
  background: var(--surface-mute);
  overflow: hidden;
  margin-top: 4px;
}

.bar span {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--brand-500), var(--accent-500));
  transition: width 0.6s ease;
}

.plan-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 11.5px;
}

.created {
  font-size: 10.5px;
  color: var(--text-mute);
}

.summary {
  display: flex;
  align-items: center;
  gap: 28px;
  margin-bottom: 18px;
}

.summary-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.summary-main h2 {
  font-size: 20px;
}

.goal-line {
  font-size: 13px;
  color: var(--text-soft);
}

.summary-text {
  font-size: 12.8px;
  color: var(--text-mute);
  line-height: 1.75;
  max-width: 620px;
}

.summary-stats {
  display: flex;
  gap: 22px;
  margin-top: 8px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--text-mute);
}

.summary-stats strong {
  color: var(--text);
  font-size: 15px;
  margin-right: 3px;
}

.days {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.day-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 9px;
}

.day-badge {
  padding: 3px 12px;
  border-radius: 999px;
  background: var(--brand-500);
  color: #fff;
  font-size: 11.5px;
  font-weight: 650;
  letter-spacing: 0.04em;
}

.day-meta {
  font-size: 11.5px;
  color: var(--text-mute);
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.task {
  display: flex;
  gap: 12px;
  padding: 14px 16px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
  background: var(--surface);
  box-shadow: var(--shadow-1);
  transition: all 0.2s ease;
}

.task:hover {
  border-color: var(--brand-100);
  box-shadow: var(--shadow-2);
}

.task.done {
  background: var(--surface-soft);
  opacity: 0.78;
}

.task.done .task-title strong {
  text-decoration: line-through;
  color: var(--text-mute);
}

.check {
  width: 21px;
  height: 21px;
  flex: none;
  margin-top: 1px;
  border-radius: 7px;
  border: 1.6px solid var(--border-strong);
  background: var(--surface);
  color: #fff;
  display: grid;
  place-items: center;
  cursor: pointer;
  transition: all 0.18s ease;
}

.check:hover {
  border-color: var(--brand-500);
}

.task.done .check {
  background: var(--success-500);
  border-color: var(--success-500);
}

.task-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.task-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.task-title strong {
  font-size: 13.5px;
  font-weight: 560;
}

.minutes {
  margin-left: auto;
  font-size: 11px;
  color: var(--text-mute);
  white-space: nowrap;
}

.task-body p {
  font-size: 12.5px;
  color: var(--text-soft);
  line-height: 1.7;
}

.points {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 2px;
}

.foot-bar {
  margin-top: 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 18px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
  background: var(--surface);
  box-shadow: var(--shadow-1);
}

@media (max-width: 720px) {
  .summary {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }
}
</style>
