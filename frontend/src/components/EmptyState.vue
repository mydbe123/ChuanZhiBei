<script setup lang="ts">
withDefaults(defineProps<{ title: string; description?: string }>(), { description: '' })
</script>

<template>
  <div class="empty">
    <div class="glyph">
      <slot name="icon" />
    </div>
    <h4>{{ title }}</h4>
    <p v-if="description">{{ description }}</p>
    <div class="action">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 46px 24px;
  gap: 8px;
  color: var(--text-soft);
}

.glyph {
  width: 54px;
  height: 54px;
  border-radius: 18px;
  display: grid;
  place-items: center;
  background: var(--brand-50);
  color: var(--brand-500);
  margin-bottom: 6px;
}

h4 {
  font-size: 15px;
  color: var(--text);
}

p {
  font-size: 13px;
  max-width: 420px;
  line-height: 1.7;
}

.action {
  margin-top: 10px;
}
</style>
