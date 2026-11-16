<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import BaseButton from './BaseButton.vue'
import AppIcon from './AppIcon.vue'

const props = defineProps<{ artifact: Record<string, unknown> }>()
const router = useRouter()

const kind = computed(() => String(props.artifact.kind ?? ''))
const title = computed(() => String(props.artifact.title ?? ''))
const visible = computed(() =>
  ['quiz', 'study_plan', 'learning_report'].includes(kind.value)
)

const meta = computed(() => {
  if (kind.value === 'quiz') {
    return `${props.artifact.questionCount ?? 0} 道题 · ${String(props.artifact.difficulty ?? 'MEDIUM')}`
  }
  if (kind.value === 'study_plan') {
    return `${props.artifact.durationDays ?? 0} 天 · ${props.artifact.totalTasks ?? 0} 个任务`
  }
  const stats = props.artifact.stats as Record<string, number> | undefined
  return stats ? `学习 ${stats.studyMinutes ?? 0} 分钟 · 打卡 ${stats.streakDays ?? 0} 天` : '学习数据已更新'
})

const caption = computed(() => {
  if (kind.value === 'quiz') return 'AI 出题'
  if (kind.value === 'study_plan') return '学习计划'
  return '学习报告'
})

const cta = computed(() => {
  if (kind.value === 'quiz') return '开始作答'
  if (kind.value === 'study_plan') return '查看计划'
  return '查看工作台'
})

function open() {
  if (kind.value === 'quiz') {
    router.push({ name: 'quiz', query: { id: String(props.artifact.quizId ?? '') } })
  } else if (kind.value === 'study_plan') {
    router.push({ name: 'plan', query: { id: String(props.artifact.planId ?? '') } })
  } else {
    router.push({ name: 'dashboard' })
  }
}
</script>

<template>
  <div v-if="visible" class="artifact">
    <div class="icon">
      <AppIcon :name="kind === 'quiz' ? 'quiz' : kind === 'study_plan' ? 'plan' : 'dashboard'" :size="19" />
    </div>
    <div class="info">
      <span class="caption">{{ caption }}</span>
      <strong>{{ title }}</strong>
      <span class="meta">{{ meta }}</span>
    </div>
    <BaseButton size="sm" variant="soft" @click="open">{{ cta }}</BaseButton>
  </div>
</template>

<style scoped>
.artifact {
  display: flex;
  align-items: center;
  gap: 13px;
  margin: 12px 0;
  padding: 13px 15px;
  border-radius: var(--radius-md);
  border: 1px solid var(--brand-100);
  background: linear-gradient(135deg, var(--brand-50), var(--surface) 70%);
  animation: fade-up 0.3s ease both;
}

.icon {
  width: 40px;
  height: 40px;
  border-radius: 13px;
  display: grid;
  place-items: center;
  background: var(--surface);
  color: var(--brand-500);
  box-shadow: var(--shadow-1);
  flex: none;
}

.info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.caption {
  font-size: 10.5px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--brand-500);
  font-weight: 650;
}

.info strong {
  font-size: 13.5px;
  font-weight: 650;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta {
  font-size: 11.5px;
  color: var(--text-mute);
}
</style>
