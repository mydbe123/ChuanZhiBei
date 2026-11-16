<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'primary' | 'soft' | 'outline' | 'ghost' | 'danger'
    size?: 'sm' | 'md' | 'lg'
    block?: boolean
    loading?: boolean
    disabled?: boolean
    type?: 'button' | 'submit'
  }>(),
  { variant: 'primary', size: 'md', block: false, loading: false, disabled: false, type: 'button' }
)
</script>

<template>
  <button
    :type="type"
    class="btn"
    :class="[`btn-${variant}`, `btn-${size}`, { 'btn-block': block, 'is-loading': loading }]"
    :disabled="disabled || loading"
  >
    <span v-if="loading" class="spinner" />
    <slot />
  </button>
</template>

<style scoped>
.btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border-radius: 999px;
  border: 1px solid transparent;
  font-weight: 560;
  cursor: pointer;
  transition: transform 0.16s ease, box-shadow 0.18s ease, background 0.18s ease, border-color 0.18s ease,
    color 0.18s ease;
  white-space: nowrap;
  letter-spacing: 0.005em;
}

.btn:active:not(:disabled) {
  transform: translateY(1px) scale(0.99);
}

.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn-sm {
  height: 30px;
  padding: 0 13px;
  font-size: 12.5px;
}

.btn-md {
  height: 38px;
  padding: 0 18px;
  font-size: 13.5px;
}

.btn-lg {
  height: 46px;
  padding: 0 24px;
  font-size: 14.5px;
}

.btn-block {
  width: 100%;
}

.btn-primary {
  background: linear-gradient(135deg, var(--brand-500), #6d5cf6);
  color: #fff;
  box-shadow: 0 8px 20px rgba(79, 70, 229, 0.26);
}

.btn-primary:hover:not(:disabled) {
  box-shadow: 0 12px 26px rgba(79, 70, 229, 0.34);
  transform: translateY(-1px);
}

.btn-soft {
  background: var(--brand-50);
  color: var(--brand-600);
}

.btn-soft:hover:not(:disabled) {
  background: var(--brand-100);
}

.btn-outline {
  background: var(--surface);
  border-color: var(--border-strong);
  color: var(--text);
}

.btn-outline:hover:not(:disabled) {
  border-color: var(--brand-300);
  color: var(--brand-600);
}

.btn-ghost {
  background: transparent;
  color: var(--text-soft);
}

.btn-ghost:hover:not(:disabled) {
  background: var(--surface-mute);
  color: var(--text);
}

.btn-danger {
  background: var(--danger-100);
  color: #b91c1c;
}

.btn-danger:hover:not(:disabled) {
  background: #fecaca;
}

.spinner {
  width: 13px;
  height: 13px;
  border-radius: 50%;
  border: 2px solid currentColor;
  border-top-color: transparent;
  animation: spin 0.75s linear infinite;
  opacity: 0.85;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
