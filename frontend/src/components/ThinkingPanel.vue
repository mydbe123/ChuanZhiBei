<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'

const props = withDefaults(defineProps<{ text: string; active?: boolean; tokens?: number }>(), {
  active: false,
  tokens: 0
})

const open = ref(true)
const hasText = computed(() => Boolean(props.text && props.text.trim()))

watch(
  () => props.active,
  (value) => {
    if (!value && props.text.length > 240) open.value = false
  }
)
</script>

<template>
  <div v-if="hasText || active" class="thinking" :class="{ live: active }">
    <button class="head" type="button" @click="open = !open">
      <span class="badge">
        <span class="pulse" />
        <AppIcon name="spark" :size="13" />
      </span>
      <span class="title">{{ active ? 'AI 正在思考…' : 'AI 思考过程' }}</span>
      <span v-if="tokens > 0" class="tokens">{{ tokens }} tokens</span>
      <span class="chevron" :class="{ up: open }">›</span>
    </button>
    <div v-show="open" class="body">
      <pre>{{ text }}<span v-if="active" class="caret" /></pre>
    </div>
  </div>
</template>

<style scoped>
.thinking {
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius-md);
  background: var(--surface-soft);
  overflow: hidden;
  margin-bottom: 12px;
}

.thinking.live {
  border-color: var(--brand-300);
}

.head {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 13px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 12.5px;
  color: var(--text-soft);
}

.badge {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 7px;
  background: var(--brand-50);
  color: var(--brand-500);
}

.pulse {
  position: absolute;
  inset: -3px;
  border-radius: 9px;
  border: 1.5px solid var(--brand-300);
  opacity: 0;
}

.live .pulse {
  animation: pulse-ring 1.6s ease-out infinite;
}

@keyframes pulse-ring {
  0% {
    opacity: 0.6;
    transform: scale(0.9);
  }
  100% {
    opacity: 0;
    transform: scale(1.25);
  }
}

.title {
  font-weight: 550;
}

.tokens {
  margin-left: auto;
  font-size: 11px;
  color: var(--text-mute);
}

.chevron {
  transition: transform 0.2s ease;
  font-size: 15px;
  color: var(--text-mute);
}

.chevron.up {
  transform: rotate(90deg);
}

.body {
  padding: 0 13px 12px;
}

pre {
  margin: 0;
  max-height: 220px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--font-sans);
  font-size: 12.5px;
  line-height: 1.72;
  color: var(--text-mute);
}

.caret {
  display: inline-block;
  width: 6px;
  height: 13px;
  margin-left: 2px;
  background: var(--brand-500);
  vertical-align: -2px;
  animation: blink 1s steps(2) infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}
</style>
