<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{ data: { label: string; value: number; hint?: string }[]; height?: number; unit?: string }>(),
  { height: 132, unit: '分钟' }
)

const max = computed(() => Math.max(1, ...props.data.map((item) => item.value)))
const bars = computed(() =>
  props.data.map((item) => ({
    ...item,
    ratio: item.value / max.value,
    percent: Math.round((item.value / max.value) * 100)
  }))
)
</script>

<template>
  <div class="chart" :style="{ height: `${height}px` }">
    <div v-for="bar in bars" :key="bar.label" class="col" :title="`${bar.hint ?? bar.label} ${bar.value} ${unit}`">
      <span class="value">{{ bar.value > 0 ? bar.value : '' }}</span>
      <div class="track">
        <div class="fill" :style="{ height: `${Math.max(bar.ratio * 100, bar.value > 0 ? 6 : 0)}%` }" />
      </div>
      <span class="label">{{ bar.label }}</span>
    </div>
  </div>
</template>

<style scoped>
.chart {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  height: 100%;
  min-width: 0;
}

.track {
  flex: 1;
  width: 100%;
  max-width: 34px;
  display: flex;
  align-items: flex-end;
  background: var(--surface-mute);
  border-radius: 10px;
  overflow: hidden;
}

.fill {
  width: 100%;
  border-radius: 10px;
  background: linear-gradient(180deg, var(--brand-300), var(--brand-500));
  transition: height 0.6s cubic-bezier(0.22, 0.8, 0.3, 1);
  animation: grow 0.7s cubic-bezier(0.22, 0.8, 0.3, 1) both;
}

.value {
  font-size: 10.5px;
  color: var(--text-mute);
  height: 13px;
  font-variant-numeric: tabular-nums;
}

.label {
  font-size: 11px;
  color: var(--text-soft);
}

@keyframes grow {
  from {
    height: 0;
  }
}
</style>
