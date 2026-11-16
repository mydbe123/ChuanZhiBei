<script setup lang="ts">
import { toasts, removeToast } from '@/stores/toast'
</script>

<template>
  <div class="toast-host">
    <TransitionGroup name="toast">
      <button
        v-for="item in toasts"
        :key="item.id"
        class="toast"
        :class="`toast-${item.type}`"
        type="button"
        @click="removeToast(item.id)"
      >
        <span class="mark" />
        <span class="msg">{{ item.message }}</span>
      </button>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed;
  top: 18px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 4000;
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  pointer-events: none;
}

.toast {
  pointer-events: auto;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 18px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--surface);
  box-shadow: var(--shadow-2);
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
  max-width: min(92vw, 520px);
  animation: fade-up 0.24s ease both;
}

.mark {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--brand-500);
  flex: none;
}

.toast-success .mark {
  background: var(--success-500);
}

.toast-error .mark {
  background: var(--danger-500);
}

.msg {
  color: var(--text);
  text-align: left;
}

.toast-enter-active,
.toast-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(-8px) scale(0.98);
}
</style>
