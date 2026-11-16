<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterView } from 'vue-router'
import ToastHost from '@/components/ToastHost.vue'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()
onMounted(() => ui.applyTheme())
</script>

<template>
  <RouterView />
  <ToastHost />
</template>
