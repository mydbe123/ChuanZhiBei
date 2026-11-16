<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{ value: number; size?: number; stroke?: number; label?: string; suffix?: string }>(),
  { size: 104, stroke: 9, label: '', suffix: '%' }
)

const radius = computed(() => (props.size - props.stroke) / 2)
const circumference = computed(() => 2 * Math.PI * radius.value)
const clamped = computed(() => Math.min(100, Math.max(0, props.value)))
const offset = computed(() => circumference.value * (1 - clamped.value / 100))
</script>

<template>
  <div class="ring" :style="{ width: `${size}px`, height: `${size}px` }">
    <svg :width="size" :height="size">
      <defs>
        <linearGradient id="zx-ring-grad" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="var(--brand-500)" />
          <stop offset="100%" stop-color="var(--accent-500)" />
        </linearGradient>
      </defs>
      <circle
        :cx="size / 2"
        :cy="size / 2"
        :r="radius"
        fill="none"
        stroke="var(--surface-mute)"
        :stroke-width="stroke"
      />
      <circle
        :cx="size / 2"
        :cy="size / 2"
        :r="radius"
        fill="none"
        stroke="url(#zx-ring-grad)"
        :stroke-width="stroke"
        stroke-linecap="round"
        :stroke-dasharray="circumference"
        :stroke-dashoffset="offset"
        :transform="`rotate(-90 ${size / 2} ${size / 2})`"
        class="bar"
      />
    </svg>
    <div class="center">
      <strong>{{ Math.round(clamped) }}<small>{{ suffix }}</small></strong>
      <span v-if="label">{{ label }}</span>
    </div>
  </div>
</template>

<style scoped>
.ring {
  position: relative;
  display: grid;
  place-items: center;
}

.bar {
  transition: stroke-dashoffset 0.7s cubic-bezier(0.22, 0.8, 0.3, 1);
}

.center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
}

strong {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

strong small {
  font-size: 11px;
  font-weight: 600;
  margin-left: 1px;
  color: var(--text-mute);
}

span {
  font-size: 10.5px;
  color: var(--text-mute);
  letter-spacing: 0.06em;
}
</style>
