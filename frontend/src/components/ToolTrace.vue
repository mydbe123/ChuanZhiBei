<script setup lang="ts">
import { ref } from 'vue'
import type { ToolTraceItem } from '@/api/types'
import AppIcon from './AppIcon.vue'

const props = defineProps<{ items: ToolTraceItem[] }>()

const expanded = ref<number | null>(null)

const LABELS: Record<string, string> = {
  search_knowledge_base: '检索知识库',
  create_study_plan: '生成学习计划',
  generate_quiz: '生成测验',
  get_learning_report: '读取学习报告'
}

function label(name: string) {
  return LABELS[name] ?? name
}

function prettyArguments(raw?: string) {
  if (!raw) return '{}'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

function toggle(index: number) {
  expanded.value = expanded.value === index ? null : index
}
</script>

<template>
  <div v-if="props.items.length" class="trace">
    <span class="trace-label">
      <AppIcon name="spark" :size="13" />
      Agent 工具链
    </span>
    <div class="chips">
      <button
        v-for="(item, index) in props.items"
        :key="`${item.name}-${index}`"
        type="button"
        class="chip-btn"
        :class="item.status === 'error' ? 'is-error' : ''"
        @click="toggle(index)"
      >
        <span class="dot" />
        {{ label(item.name) }}
      </button>
    </div>
    <div v-if="expanded !== null" class="detail">
      <div class="detail-head">{{ label(props.items[expanded].name) }}</div>
      <pre class="args">{{ prettyArguments(props.items[expanded].arguments) }}</pre>
      <p v-if="props.items[expanded].summary" class="summary">{{ props.items[expanded].summary }}</p>
    </div>
  </div>
</template>

<style scoped>
.trace {
  margin: 10px 0 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.trace-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  letter-spacing: 0.04em;
  color: var(--text-mute);
  text-transform: uppercase;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.chip-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 11px;
  border-radius: 999px;
  border: 1px solid var(--brand-100);
  background: var(--brand-50);
  color: var(--brand-600);
  font-size: 12px;
  font-weight: 550;
  cursor: pointer;
  transition: all 0.18s ease;
}

.chip-btn:hover {
  border-color: var(--brand-300);
}

.chip-btn.is-error {
  border-color: #fecaca;
  background: var(--danger-100);
  color: #b91c1c;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--success-500);
}

.is-error .dot {
  background: var(--danger-500);
}

.detail {
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
  padding: 10px 12px;
  animation: fade-up 0.2s ease both;
}

.detail-head {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-soft);
  margin-bottom: 6px;
}

.args {
  margin: 0;
  font-family: var(--font-mono);
  font-size: 11.5px;
  color: var(--text-soft);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 140px;
  overflow: auto;
}

.summary {
  margin-top: 8px;
  font-size: 12.5px;
  color: var(--text);
}
</style>
