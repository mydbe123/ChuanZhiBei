<script setup lang="ts">
withDefaults(defineProps<{ hover?: boolean; pad?: 'none' | 'sm' | 'md' | 'lg' }>(), {
  hover: false,
  pad: 'md'
})
</script>

<template>
  <section class="card" :class="[`pad-${pad}`, { hover }]">
    <slot />
  </section>
</template>

<style scoped>
.card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-1);
  transition: box-shadow 0.22s ease, transform 0.22s ease, border-color 0.22s ease;
}

.pad-none {
  padding: 0;
}

.pad-sm {
  padding: 14px;
}

.pad-md {
  padding: 20px;
}

.pad-lg {
  padding: 26px;
}

.hover:hover {
  box-shadow: var(--shadow-2);
  transform: translateY(-2px);
  border-color: var(--brand-100);
}
</style>
