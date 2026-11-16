<script setup lang="ts">
import type { Citation } from '@/api/types'
import AppIcon from './AppIcon.vue'

defineProps<{ items: Citation[] }>()
</script>

<template>
  <div v-if="items.length" class="citations">
    <div class="head">
      <AppIcon name="knowledge" :size="13" />
      <span>引用来源 · {{ items.length }}</span>
    </div>
    <ol>
      <li v-for="(item, index) in items" :key="`${item.documentTitle}-${item.seq}-${index}`">
        <span class="idx">{{ item.index ?? index + 1 }}</span>
        <div class="body">
          <div class="title">
            {{ item.documentTitle }}
            <span class="seq">第 {{ item.seq }} 段</span>
            <span class="score">{{ (item.score * 100).toFixed(0) }}%</span>
          </div>
          <p>{{ item.snippet }}</p>
        </div>
      </li>
    </ol>
  </div>
</template>

<style scoped>
.citations {
  margin-top: 14px;
  border-top: 1px dashed var(--border-strong);
  padding-top: 12px;
}

.head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--text-mute);
  margin-bottom: 8px;
}

ol {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

li {
  display: flex;
  gap: 9px;
  padding: 9px 11px;
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
  border: 1px solid var(--border);
}

.idx {
  flex: none;
  width: 19px;
  height: 19px;
  border-radius: 6px;
  background: var(--brand-50);
  color: var(--brand-600);
  font-size: 11px;
  font-weight: 700;
  display: grid;
  place-items: center;
}

.body {
  min-width: 0;
}

.title {
  font-size: 12.5px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.seq {
  font-weight: 400;
  color: var(--text-mute);
  font-size: 11.5px;
}

.score {
  margin-left: auto;
  color: var(--success-500);
  font-size: 11.5px;
  font-weight: 600;
}

p {
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--text-soft);
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
