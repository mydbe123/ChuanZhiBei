<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from '@/components/AppIcon.vue'
import BaseButton from '@/components/BaseButton.vue'
import BaseCard from '@/components/BaseCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import ProgressRing from '@/components/ProgressRing.vue'
import { api } from '@/api/client'
import type { QuizDetail, QuizSummary, SubmitResult } from '@/api/types'
import { toast } from '@/stores/toast'
import { formatRelative } from '@/utils/format'

type Mode = 'list' | 'play' | 'result'

const route = useRoute()
const router = useRouter()

const mode = ref<Mode>('list')
const quizzes = ref<QuizSummary[]>([])
const detail = ref<QuizDetail | null>(null)
const result = ref<SubmitResult | null>(null)
const answers = reactive<Record<number, string>>({})

const loading = ref(true)
const generating = ref(false)
const submitting = ref(false)
const openingId = ref<number | null>(null)

const form = reactive({ topic: '', difficulty: 'MEDIUM', count: 5 })

const DIFFICULTY_LABEL: Record<string, string> = {
  EASY: '基础',
  MEDIUM: '进阶',
  HARD: '挑战'
}

const answeredCount = computed(() => {
  if (!detail.value) return 0
  return detail.value.questions.filter((q) => answers[q.id]).length
})

const progress = computed(() => {
  if (!detail.value?.questions.length) return 0
  return (answeredCount.value / detail.value.questions.length) * 100
})

const scoreTone = computed(() => {
  const score = result.value?.score ?? 0
  if (score >= 85) return 'great'
  if (score >= 60) return 'ok'
  return 'weak'
})

onMounted(async () => {
  await loadList()
  const id = route.query.id
  if (typeof id === 'string' && id) {
    await openQuiz(Number(id))
  }
})

async function loadList() {
  loading.value = true
  try {
    quizzes.value = await api.get<QuizSummary[]>('/quiz')
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
    const created = await api.post<QuizDetail>('/quiz/generate', {
      topic: form.topic.trim() || '数据结构与算法基础',
      difficulty: form.difficulty,
      count: form.count
    })
    toast.success(`已生成「${created.title}」`)
    await loadList()
    await openQuiz(created.id)
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    generating.value = false
  }
}

async function openQuiz(id: number) {
  if (!id || Number.isNaN(id)) return
  openingId.value = id
  try {
    const data = await api.get<QuizDetail>(`/quiz/${id}`)
    detail.value = data
    Object.keys(answers).forEach((key) => delete answers[Number(key)])
    if (data.status === 'DONE') {
      result.value = await api.get<SubmitResult>(`/quiz/${id}/result`)
      mode.value = 'result'
    } else {
      result.value = null
      mode.value = 'play'
    }
    if (route.query.id !== String(id)) {
      router.replace({ name: 'quiz', query: { id: String(id) } })
    }
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    openingId.value = null
  }
}

function backToList() {
  mode.value = 'list'
  detail.value = null
  result.value = null
  router.replace({ name: 'quiz' })
  void loadList()
}

async function submit() {
  if (!detail.value || submitting.value) return
  if (answeredCount.value < detail.value.questions.length) {
    toast.error(`还有 ${detail.value.questions.length - answeredCount.value} 题未作答`)
    return
  }
  submitting.value = true
  try {
    result.value = await api.post<SubmitResult>(`/quiz/${detail.value.id}/submit`, {
      answers: detail.value.questions.map((q) => ({ questionId: q.id, answer: answers[q.id] }))
    })
    mode.value = 'result'
    toast.success(`提交成功，得分 ${result.value.score}`)
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">
          <AppIcon name="quiz" :size="22" />
          智能测验
        </h1>
        <p class="page-sub">
          AI 依据主题与你上传的资料命题，提交后自动批改并生成学情诊断，错题知识点会进入掌握度统计。
        </p>
      </div>
      <BaseButton v-if="mode !== 'list'" variant="outline" @click="backToList">
        <AppIcon name="arrow" :size="15" />
        返回列表
      </BaseButton>
    </div>

    <!-- 列表 -->
    <template v-if="mode === 'list'">
      <BaseCard class="creator">
        <div class="creator-grid">
          <label class="field grow">
            <span>测验主题</span>
            <input
              v-model="form.topic"
              placeholder="例如：二叉树遍历 / 进程调度 / B+ 树索引"
              @keydown.enter="generate"
            />
          </label>
          <label class="field">
            <span>难度</span>
            <select v-model="form.difficulty">
              <option value="EASY">基础</option>
              <option value="MEDIUM">进阶</option>
              <option value="HARD">挑战</option>
            </select>
          </label>
          <label class="field">
            <span>题量</span>
            <select v-model="form.count">
              <option :value="3">3 题</option>
              <option :value="5">5 题</option>
              <option :value="8">8 题</option>
              <option :value="10">10 题</option>
            </select>
          </label>
          <BaseButton size="lg" :loading="generating" @click="generate">
            <AppIcon name="spark" :size="16" />
            {{ generating ? 'AI 正在命题…' : '生成测验' }}
          </BaseButton>
        </div>
        <p class="creator-hint">
          生成过程会调用 Agent 出题工具，通常需要 10-30 秒；题目会结合你知识库中的相关资料。
        </p>
      </BaseCard>

      <div v-if="loading" class="grid grid-3">
        <div v-for="i in 3" :key="i" class="skeleton" style="height: 132px; border-radius: 20px" />
      </div>

      <div v-else-if="quizzes.length" class="grid grid-3">
        <BaseCard v-for="quiz in quizzes" :key="quiz.id" hover class="quiz-card">
          <div class="quiz-top">
            <span class="chip chip-brand">{{ DIFFICULTY_LABEL[quiz.difficulty] ?? quiz.difficulty }}</span>
            <span v-if="quiz.score !== null" class="score-badge" :class="{ pass: quiz.score >= 60 }">
              {{ quiz.score }} 分
            </span>
            <span v-else class="chip">未作答</span>
          </div>
          <h3>{{ quiz.title }}</h3>
          <p class="topic">{{ quiz.topic }}</p>
          <div class="quiz-foot">
            <span class="muted">{{ quiz.questionCount }} 题 · {{ formatRelative(quiz.createdAt) }}</span>
            <BaseButton size="sm" variant="soft" :loading="openingId === quiz.id" @click="openQuiz(quiz.id)">
              {{ quiz.status === 'DONE' ? '查看解析' : '开始作答' }}
            </BaseButton>
          </div>
        </BaseCard>
      </div>

      <BaseCard v-else>
        <EmptyState
          title="还没有测验"
          description="输入一个主题，让 AI 为你定制一套单选题，检验真实掌握程度"
        >
          <template #icon><AppIcon name="quiz" :size="22" /></template>
        </EmptyState>
      </BaseCard>
    </template>

    <!-- 作答 -->
    <template v-else-if="mode === 'play' && detail">
      <div class="play-head">
        <div>
          <h2>{{ detail.title }}</h2>
          <p class="muted">
            {{ detail.topic }} · {{ detail.questions.length }} 题 ·
            {{ DIFFICULTY_LABEL[detail.difficulty] ?? detail.difficulty }}
          </p>
        </div>
        <div class="progress-wrap">
          <span>{{ answeredCount }} / {{ detail.questions.length }}</span>
          <div class="progress"><span :style="{ width: `${progress}%` }" /></div>
        </div>
      </div>

      <div class="questions">
        <BaseCard v-for="(question, index) in detail.questions" :key="question.id" class="question">
          <div class="q-head">
            <span class="q-index">{{ index + 1 }}</span>
            <span v-if="question.knowledgePoint" class="chip">{{ question.knowledgePoint }}</span>
          </div>
          <p class="stem">{{ question.stem }}</p>
          <div class="options">
            <label
              v-for="option in question.options"
              :key="option.key"
              class="option"
              :class="{ selected: answers[question.id] === option.key }"
            >
              <input v-model="answers[question.id]" type="radio" :value="option.key" />
              <span class="key">{{ option.key }}</span>
              <span class="text">{{ option.text }}</span>
            </label>
          </div>
        </BaseCard>
      </div>

      <div class="submit-bar">
        <span class="muted">提交后立即批改，并生成 AI 学情诊断</span>
        <BaseButton size="lg" :loading="submitting" @click="submit">
          <AppIcon name="check" :size="16" />
          提交答卷
        </BaseButton>
      </div>
    </template>

    <!-- 结果 -->
    <template v-else-if="mode === 'result' && result">
      <BaseCard class="result-head">
        <ProgressRing :value="result.score" :size="124" label="得分" />
        <div class="result-info">
          <span class="eyebrow" :class="scoreTone">
            {{ result.score >= 85 ? '表现出色' : result.score >= 60 ? '基本掌握' : '需要加强' }}
          </span>
          <h2>{{ result.title }}</h2>
          <p class="muted">
            答对 {{ result.correctCount }} / {{ result.total }} 题 · 正确率
            {{ Math.round((result.correctCount / Math.max(1, result.total)) * 100) }}%
          </p>
          <div v-if="result.weakPoints.length" class="weak">
            <span class="muted">待加强：</span>
            <span v-for="point in result.weakPoints" :key="point" class="chip chip-warn">{{ point }}</span>
          </div>
        </div>
      </BaseCard>

      <BaseCard class="diagnosis">
        <div class="diag-head">
          <span class="diag-icon"><AppIcon name="spark" :size="16" /></span>
          <strong>AI 学情诊断</strong>
        </div>
        <p>{{ result.diagnosis }}</p>
      </BaseCard>

      <div class="questions">
        <BaseCard v-for="(item, index) in result.results" :key="item.questionId" class="question">
          <div class="q-head">
            <span class="q-index" :class="item.correct ? 'ok' : 'bad'">{{ index + 1 }}</span>
            <span class="chip" :class="item.correct ? 'chip-success' : 'chip-danger'">
              {{ item.correct ? '回答正确' : '回答错误' }}
            </span>
            <span v-if="item.knowledgePoint" class="chip">{{ item.knowledgePoint }}</span>
          </div>
          <p class="stem">{{ item.stem }}</p>
          <div class="options review">
            <div
              v-for="option in item.options"
              :key="option.key"
              class="option"
              :class="{
                correct: option.key === item.correctAnswer,
                wrong: option.key === item.userAnswer && !item.correct
              }"
            >
              <span class="key">{{ option.key }}</span>
              <span class="text">{{ option.text }}</span>
              <span v-if="option.key === item.correctAnswer" class="tag">正确答案</span>
              <span v-else-if="option.key === item.userAnswer" class="tag bad">你的选择</span>
            </div>
          </div>
          <div v-if="item.explanation" class="explain">
            <strong>解析</strong>
            <p>{{ item.explanation }}</p>
          </div>
        </BaseCard>
      </div>

      <div class="submit-bar">
        <BaseButton variant="outline" @click="backToList">返回列表</BaseButton>
        <BaseButton @click="router.push({ name: 'tutor' })">
          <AppIcon name="tutor" :size="15" />
          让 AI 导师讲解薄弱点
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
  min-width: 118px;
}

.field.grow {
  flex: 1;
  min-width: 240px;
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

.quiz-card {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.quiz-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.score-badge {
  font-size: 12px;
  font-weight: 700;
  padding: 2px 9px;
  border-radius: 999px;
  background: var(--danger-100);
  color: #b91c1c;
}

.score-badge.pass {
  background: var(--success-100);
  color: #047857;
}

.quiz-card h3 {
  font-size: 14.5px;
  line-height: 1.45;
}

.topic {
  font-size: 12px;
  color: var(--text-mute);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.quiz-foot {
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 11.5px;
}

.play-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.play-head h2 {
  font-size: 19px;
}

.play-head p {
  font-size: 12.5px;
  margin-top: 4px;
}

.progress-wrap {
  min-width: 190px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  color: var(--text-soft);
}

.progress {
  height: 7px;
  border-radius: 999px;
  background: var(--surface-mute);
  overflow: hidden;
}

.progress span {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--brand-500), var(--accent-500));
  transition: width 0.3s ease;
}

.questions {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.question {
  animation: fade-up 0.3s ease both;
}

.q-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.q-index {
  width: 24px;
  height: 24px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: var(--brand-500);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.q-index.ok {
  background: var(--success-500);
}

.q-index.bad {
  background: var(--danger-500);
}

.stem {
  font-size: 14.5px;
  line-height: 1.75;
  margin-bottom: 14px;
  font-weight: 520;
}

.options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.option {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 11px 13px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--surface-soft);
  cursor: pointer;
  transition: all 0.18s ease;
  font-size: 13.5px;
  line-height: 1.65;
}

.option input {
  display: none;
}

.option:hover {
  border-color: var(--brand-300);
}

.option.selected {
  border-color: var(--brand-500);
  background: var(--brand-50);
}

.option .key {
  width: 21px;
  height: 21px;
  flex: none;
  border-radius: 6px;
  display: grid;
  place-items: center;
  background: var(--surface);
  border: 1px solid var(--border-strong);
  font-size: 11.5px;
  font-weight: 700;
  color: var(--text-soft);
  margin-top: 1px;
}

.option.selected .key {
  background: var(--brand-500);
  border-color: var(--brand-500);
  color: #fff;
}

.options.review .option {
  cursor: default;
}

.option.correct {
  border-color: var(--success-500);
  background: rgba(16, 185, 129, 0.09);
}

.option.wrong {
  border-color: var(--danger-500);
  background: rgba(239, 68, 68, 0.08);
}

.tag {
  margin-left: auto;
  font-size: 10.5px;
  font-weight: 650;
  color: var(--success-500);
  white-space: nowrap;
}

.tag.bad {
  color: var(--danger-500);
}

.explain {
  margin-top: 12px;
  padding: 11px 13px;
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
  border-left: 3px solid var(--brand-300);
}

.explain strong {
  font-size: 12px;
  color: var(--brand-600);
}

.explain p {
  margin-top: 4px;
  font-size: 12.8px;
  color: var(--text-soft);
  line-height: 1.7;
}

.submit-bar {
  position: sticky;
  bottom: 16px;
  margin-top: 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 88%, transparent);
  backdrop-filter: blur(12px);
  box-shadow: var(--shadow-2);
}

.result-head {
  display: flex;
  align-items: center;
  gap: 26px;
  margin-bottom: 14px;
}

.result-info {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.eyebrow {
  font-size: 11px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  font-weight: 700;
  color: var(--text-mute);
}

.eyebrow.great {
  color: var(--success-500);
}

.eyebrow.ok {
  color: var(--brand-500);
}

.eyebrow.weak {
  color: var(--warn-500);
}

.result-info h2 {
  font-size: 19px;
}

.weak {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
  margin-top: 6px;
  font-size: 12px;
}

.diagnosis {
  margin-bottom: 16px;
  border-left: 3px solid var(--brand-500);
}

.diag-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.diag-icon {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: var(--brand-50);
  color: var(--brand-500);
}

.diagnosis strong {
  font-size: 13px;
}

.diagnosis p {
  font-size: 13.5px;
  line-height: 1.8;
  color: var(--text-soft);
}

@media (max-width: 720px) {
  .result-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }
}
</style>
