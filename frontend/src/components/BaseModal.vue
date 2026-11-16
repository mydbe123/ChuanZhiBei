<script setup lang="ts">
import AppIcon from './AppIcon.vue'

withDefaults(defineProps<{ open: boolean; title?: string; width?: string }>(), { width: '520px' })
const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="open" class="overlay" @click.self="emit('close')">
        <div class="panel" :style="{ width }">
          <header class="head">
            <h3>{{ title }}</h3>
            <button class="close" type="button" aria-label="关闭" @click="emit('close')">
              <AppIcon name="close" :size="17" />
            </button>
          </header>
          <div class="body">
            <slot />
          </div>
          <footer v-if="$slots.footer" class="foot">
            <slot name="footer" />
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(10, 14, 26, 0.45);
  backdrop-filter: blur(4px);
}

.panel {
  max-width: 100%;
  max-height: 88vh;
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-3);
  overflow: hidden;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
}

.head h3 {
  font-size: 15.5px;
}

.close {
  border: none;
  background: transparent;
  color: var(--text-mute);
  cursor: pointer;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: grid;
  place-items: center;
}

.close:hover {
  background: var(--surface-mute);
  color: var(--text);
}

.body {
  padding: 20px;
  overflow-y: auto;
}

.foot {
  padding: 14px 20px;
  border-top: 1px solid var(--border);
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  background: var(--surface-soft);
}

.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}

.modal-enter-active .panel {
  transition: transform 0.24s cubic-bezier(0.22, 0.8, 0.3, 1);
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .panel {
  transform: translateY(14px) scale(0.98);
}
</style>
