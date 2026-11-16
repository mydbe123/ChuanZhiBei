<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const props = defineProps<{ content: string }>()

marked.setOptions({ gfm: true, breaks: true })

// AI 输出统一经 DOMPurify 净化，避免模型被诱导输出 <script>/onerror 等注入内容
const html = computed(() => {
  const raw = marked.parse(props.content ?? '', { async: false }) as string
  return DOMPurify.sanitize(raw, { USE_PROFILES: { html: true } })
})
</script>

<template>
  <div class="md" v-html="html" />
</template>
