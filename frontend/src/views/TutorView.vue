<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import ArtifactCard from '@/components/ArtifactCard.vue'
import BaseButton from '@/components/BaseButton.vue'
import CitationList from '@/components/CitationList.vue'
import MarkdownView from '@/components/MarkdownView.vue'
import ThinkingPanel from '@/components/ThinkingPanel.vue'
import ToolTrace from '@/components/ToolTrace.vue'
import { api } from '@/api/client'
import { streamChat } from '@/api/stream'
import type { ChatMessage, Citation, Conversation, ToolTraceItem } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/stores/toast'
import { formatRelative } from '@/utils/format'

interface MessageView {
  id: number
  role: 'user' | 'assistant'
  content: string
  reasoning?: string | null
  toolTrace?: ToolTraceItem[] | null
  citations?: Citation[] | null
  createdAt?: string
}

const auth = useAuthStore()

const conversations = ref<Conversation[]>([])
const activeId = ref<number | null>(null)
const messages = ref<ChatMessage[]>([])
const input = ref('')
const images = ref<string[]>([])
const useKnowledge = ref(true)
const streaming = ref(false)
const loadingHistory = ref(false)
const controller = ref<AbortController | null>(null)
const step = ref({ current: 0, max: 0 })
const listOpen = ref(false)
const listening = ref(false)
const speakingId = ref<string | number | null>(null)

const scroller = ref<HTMLElement | null>(null)
const textarea = ref<HTMLTextAreaElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

const SUGGESTIONS = [
  '用生活中的例子解释二叉树的前中后序遍历',
  '帮我在知识库里查一下「进程调度」，并总结要点',
  '根据我最近的学习数据，给一条最值得做的改进建议',
  '帮我制定一个 14 天的数据结构冲刺计划',
  '出 5 道关于动态规划的单选题'
]

const activeTitle = computed(
  () => conversations.value.find((item) => item.id === activeId.value)?.title ?? '新的对话'
)

const toolbarStep = computed(() => (streaming.value && step.value.max > 0 ? step.value : null))

onMounted(async () => {
  await loadConversations()
  if (conversations.value.length) {
    await selectConversation(conversations.value[0].id)
  }
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  controller.value?.abort()
  document.removeEventListener('keydown', onKeydown)
  if ('speechSynthesis' in window) window.speechSynthesis.cancel()
})

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') listOpen.value = false
}

async function loadConversations() {
  try {
    conversations.value = await api.get<Conversation[]>('/chat/conversations')
  } catch (e) {
    toast.error((e as Error).message)
  }
}

async function selectConversation(id: number) {
  if (streaming.value) return
  activeId.value = id
  listOpen.value = false
  loadingHistory.value = true
  try {
    const history = await api.get<MessageView[]>(`/chat/conversations/${id}/messages`)
    messages.value = history.map((item) => ({
      id: item.id,
      role: item.role,
      content: item.content ?? '',
      reasoning: item.reasoning,
      toolTrace: item.toolTrace ?? [],
      citations: item.citations ?? [],
      createdAt: item.createdAt
    }))
    await scrollToBottom(true)
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    loadingHistory.value = false
  }
}

function newChat() {
  if (streaming.value) return
  activeId.value = null
  messages.value = []
  images.value = []
  listOpen.value = false
  nextTick(() => textarea.value?.focus())
}

async function removeConversation(event: MouseEvent, id: number) {
  event.stopPropagation()
  try {
    await api.del(`/chat/conversations/${id}`)
    conversations.value = conversations.value.filter((item) => item.id !== id)
    if (activeId.value === id) newChat()
    toast.success('会话已删除')
  } catch (e) {
    toast.error((e as Error).message)
  }
}

function useSuggestion(text: string) {
  input.value = text
  send()
}

async function send() {
  if (streaming.value) return
  const text = input.value.trim()
  if (!text && images.value.length === 0) return

  const outgoingImages = [...images.value]
  messages.value.push({
    id: `u-${Date.now()}`,
    role: 'user',
    content: text || '请帮我看看这道题',
    images: outgoingImages
  })
  const assistant: ChatMessage = {
    id: `a-${Date.now()}`,
    role: 'assistant',
    content: '',
    reasoning: '',
    toolTrace: [],
    citations: [],
    streaming: true
  }
  messages.value.push(assistant)

  input.value = ''
  images.value = []
  resizeTextarea()
  streaming.value = true
  step.value = { current: 0, max: 0 }
  controller.value = new AbortController()
  await scrollToBottom(true)

  try {
    await streamChat(
      {
        conversationId: activeId.value,
        content: text,
        images: outgoingImages,
        useKnowledge: useKnowledge.value
      },
      {
        onMeta: (data) => {
          if (typeof data.conversationId === 'number') {
            activeId.value = data.conversationId
          }
          const title = typeof data.title === 'string' ? data.title : ''
          if (activeId.value !== null) upsertConversation(activeId.value, title)
        },
        onStep: (data) => {
          step.value = {
            current: Number(data.step ?? 0),
            max: Number(data.maxSteps ?? 0)
          }
        },
        onReasoning: (delta) => {
          assistant.reasoning = (assistant.reasoning ?? '') + delta
          scrollToBottom()
        },
        onDelta: (delta) => {
          assistant.content += delta
          scrollToBottom()
        },
        onToolCall: (data) => {
          assistant.toolTrace = [
            ...(assistant.toolTrace ?? []),
            {
              id: String(data.id ?? ''),
              name: String(data.name ?? ''),
              arguments: String(data.arguments ?? ''),
              status: 'running'
            }
          ]
          scrollToBottom()
        },
        onToolResult: (data) => {
          const id = String(data.id ?? '')
          const trace = assistant.toolTrace ?? []
          for (let i = trace.length - 1; i >= 0; i--) {
            if (trace[i].id === id) {
              trace[i] = {
                ...trace[i],
                status: 'success',
                summary: String(data.summary ?? ''),
                artifact: (data.artifact as Record<string, unknown>) ?? null
              }
              break
            }
          }
          assistant.toolTrace = [...trace]
          scrollToBottom()
        },
        onCitations: (items) => {
          assistant.citations = items as Citation[]
        },
        onDone: () => {
          assistant.streaming = false
          void loadConversations()
        },
        onError: (message) => {
          assistant.error = true
          assistant.streaming = false
          assistant.content = assistant.content || message
          toast.error(message)
        }
      },
      controller.value.signal
    )
  } catch (e) {
    assistant.error = true
    assistant.content = assistant.content || (e as Error).message
  } finally {
    assistant.streaming = false
    streaming.value = false
    controller.value = null
    step.value = { current: 0, max: 0 }
    await scrollToBottom()
  }
}

function upsertConversation(id: number, title: string) {
  const index = conversations.value.findIndex((item) => item.id === id)
  if (index >= 0) {
    conversations.value[index] = {
      ...conversations.value[index],
      title: title || conversations.value[index].title,
      updatedAt: new Date().toISOString()
    }
  } else {
    conversations.value.unshift({
      id,
      title: title || '新的对话',
      scene: 'TUTOR',
      messageCount: 0,
      updatedAt: new Date().toISOString()
    })
  }
}

function stop() {
  controller.value?.abort()
  streaming.value = false
  const last = messages.value[messages.value.length - 1]
  if (last && last.streaming) last.streaming = false
  toast.info('已停止生成')
}

function onEnter(event: KeyboardEvent) {
  if (event.shiftKey) return
  event.preventDefault()
  send()
}

function resizeTextarea() {
  nextTick(() => {
    const el = textarea.value
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, 168)}px`
  })
}

function onInput() {
  resizeTextarea()
}

async function scrollToBottom(force = false) {
  await nextTick()
  const el = scroller.value
  if (!el) return
  const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 220
  if (force || nearBottom) {
    el.scrollTop = el.scrollHeight
  }
}

function pickImage() {
  fileInput.value?.click()
}

function onFiles(event: Event) {
  const target = event.target as HTMLInputElement
  const files = target.files
  if (!files) return
  Array.from(files).forEach((file) => {
    if (!file.type.startsWith('image/')) return
    if (file.size > 4 * 1024 * 1024) {
      toast.error('单张图片需小于 4MB')
      return
    }
    const reader = new FileReader()
    reader.onload = () => {
      if (typeof reader.result === 'string') images.value.push(reader.result)
    }
    reader.readAsDataURL(file)
  })
  target.value = ''
}

function removeImage(index: number) {
  images.value.splice(index, 1)
}

// ---- 语音输入（Web Speech API） ----
type SpeechRecognitionLike = {
  lang: string
  interimResults: boolean
  continuous: boolean
  start: () => void
  stop: () => void
  onresult: ((event: { resultIndex: number; results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void) | null
  onend: (() => void) | null
  onerror: (() => void) | null
}
type SpeechRecognitionCtor = new () => SpeechRecognitionLike

const recognitionCtor: SpeechRecognitionCtor | null =
  (window as unknown as { SpeechRecognition?: SpeechRecognitionCtor }).SpeechRecognition ??
  (window as unknown as { webkitSpeechRecognition?: SpeechRecognitionCtor }).webkitSpeechRecognition ??
  null

let recognition: SpeechRecognitionLike | null = null

function toggleVoice() {
  if (!recognitionCtor) {
    toast.info('当前浏览器暂不支持语音输入，推荐使用 Chrome / Edge')
    return
  }
  if (listening.value) {
    recognition?.stop()
    return
  }
  recognition = new recognitionCtor()
  recognition.lang = 'zh-CN'
  recognition.interimResults = true
  recognition.continuous = false
  recognition.onresult = (event) => {
    let text = ''
    for (let i = event.resultIndex; i < event.results.length; i++) {
      text += event.results[i][0].transcript
    }
    input.value = text
    resizeTextarea()
  }
  recognition.onend = () => {
    listening.value = false
  }
  recognition.onerror = () => {
    listening.value = false
    toast.error('语音识别失败，请检查麦克风权限')
  }
  recognition.start()
  listening.value = true
}

// ---- 朗读 ----
function stripMarkdown(text: string) {
  return text
    .replace(/```[\s\S]*?```/g, '（代码块）')
    .replace(/[*_#>`~]/g, '')
    .replace(/\[(.*?)\]\(.*?\)/g, '$1')
    .trim()
}

function speak(message: ChatMessage) {
  if (!('speechSynthesis' in window)) {
    toast.info('当前浏览器不支持语音播报')
    return
  }
  if (speakingId.value === message.id) {
    window.speechSynthesis.cancel()
    speakingId.value = null
    return
  }
  window.speechSynthesis.cancel()
  const utterance = new SpeechSynthesisUtterance(stripMarkdown(message.content))
  utterance.lang = 'zh-CN'
  utterance.rate = 1.02
  utterance.onend = () => {
    speakingId.value = null
  }
  speakingId.value = message.id
  window.speechSynthesis.speak(utterance)
}

async function copy(message: ChatMessage) {
  try {
    await navigator.clipboard.writeText(message.content)
    toast.success('已复制到剪贴板')
  } catch {
    toast.error('复制失败，请手动选择文本')
  }
}

function artifactsOf(message: ChatMessage) {
  return (message.toolTrace ?? [])
    .map((item) => item.artifact)
    .filter((artifact): artifact is Record<string, unknown> =>
      Boolean(artifact && typeof artifact === 'object' && 'kind' in artifact)
    )
}
</script>

<template>
  <div class="tutor">
    <aside class="conv" :class="{ open: listOpen }">
      <div class="conv-head">
        <strong>对话历史</strong>
        <BaseButton size="sm" variant="soft" @click="newChat">
          <AppIcon name="plus" :size="14" />
          新对话
        </BaseButton>
      </div>
      <div class="conv-list">
        <button
          v-for="item in conversations"
          :key="item.id"
          type="button"
          class="conv-item"
          :class="{ active: item.id === activeId }"
          @click="selectConversation(item.id)"
        >
          <div class="conv-text">
            <span class="title">{{ item.title }}</span>
            <span class="meta">{{ item.messageCount }} 条 · {{ formatRelative(item.updatedAt) }}</span>
          </div>
          <span class="del" title="删除会话" @click="removeConversation($event, item.id)">
            <AppIcon name="trash" :size="14" />
          </span>
        </button>
        <p v-if="!conversations.length" class="conv-empty">还没有对话，问点什么开始吧</p>
      </div>
    </aside>

    <section class="chat">
      <header class="chat-head">
        <button class="icon-btn mobile-only" type="button" @click="listOpen = !listOpen">
          <AppIcon name="menu" :size="17" />
        </button>
        <div class="chat-title">
          <strong>{{ activeTitle }}</strong>
          <span>
            <span class="live-dot" :class="{ on: streaming }" />
            {{ streaming ? '正在生成…' : 'AI 导师在线' }}
            <template v-if="toolbarStep">
              · 第 {{ toolbarStep.current }}/{{ toolbarStep.max }} 步
            </template>
          </span>
        </div>
        <div class="spacer" />
        <label class="switch" :title="useKnowledge ? '已开启知识库增强' : '未开启知识库增强'">
          <input v-model="useKnowledge" type="checkbox" />
          <span class="track"><span class="thumb" /></span>
          <span class="switch-label">
            <AppIcon name="knowledge" :size="13" />
            RAG
          </span>
        </label>
      </header>

      <div ref="scroller" class="messages">
        <div v-if="loadingHistory" class="loading">
          <div class="skeleton" style="height: 64px; width: 70%" />
          <div class="skeleton" style="height: 96px; width: 86%" />
        </div>

        <div v-else-if="!messages.length" class="welcome">
          <div class="welcome-icon">
            <AppIcon name="tutor" :size="26" />
          </div>
          <h2>你好，{{ auth.user?.displayName ?? '同学' }} 👋</h2>
          <p>
            我是你的 AI 学习导师。可以讲题、查你的知识库、出题、制定计划，也会记住你的学习数据。
          </p>
          <div class="suggestions">
            <button
              v-for="text in SUGGESTIONS"
              :key="text"
              type="button"
              class="suggestion"
              @click="useSuggestion(text)"
            >
              {{ text }}
            </button>
          </div>
        </div>

        <div
          v-for="message in messages"
          :key="message.id"
          class="msg"
          :class="message.role"
        >
          <div v-if="message.role === 'assistant'" class="avatar ai">
            <AppIcon name="spark" :size="16" />
          </div>
          <div class="bubble-wrap">
            <div class="bubble" :class="{ error: message.error }">
              <template v-if="message.role === 'user'">
                <div v-if="message.images?.length" class="msg-images">
                  <img v-for="(src, i) in message.images" :key="i" :src="src" alt="上传的题目图片" />
                </div>
                <p class="plain">{{ message.content }}</p>
              </template>

              <template v-else>
                <ThinkingPanel
                  v-if="message.reasoning || message.streaming"
                  :text="message.reasoning ?? ''"
                  :active="Boolean(message.streaming)"
                />
                <ToolTrace :items="message.toolTrace ?? []" />
                <ArtifactCard
                  v-for="(artifact, index) in artifactsOf(message)"
                  :key="index"
                  :artifact="artifact"
                />
                <MarkdownView v-if="message.content" :content="message.content" />
                <div v-else-if="message.streaming" class="typing">
                  <span /><span /><span />
                </div>
                <CitationList v-if="message.citations?.length" :items="message.citations" />
              </template>
            </div>

            <div v-if="message.role === 'assistant' && !message.streaming" class="msg-actions">
              <button type="button" @click="copy(message)">
                {{ speakingId === message.id ? '朗读中' : '朗读' }}
              </button>
              <button type="button" @click="speak(message)">
                {{ speakingId === message.id ? '停止' : '播放' }}
              </button>
              <span class="stamp">AI 生成 · 请核验关键信息</span>
            </div>
          </div>
          <div v-if="message.role === 'user'" class="avatar user" :style="{ background: auth.user?.avatarColor }">
            {{ auth.initial }}
          </div>
        </div>
      </div>

      <footer class="composer">
        <div v-if="images.length" class="previews">
          <div v-for="(src, index) in images" :key="index" class="preview">
            <img :src="src" alt="待发送图片" />
            <button type="button" @click="removeImage(index)"><AppIcon name="close" :size="12" /></button>
          </div>
        </div>
        <div class="input-row">
          <button class="tool-btn" type="button" title="上传题目图片" @click="pickImage">
            <AppIcon name="image" :size="18" />
          </button>
          <button
            class="tool-btn"
            type="button"
            :class="{ recording: listening }"
            title="语音输入"
            @click="toggleVoice"
          >
            <AppIcon name="mic" :size="18" />
          </button>
          <textarea
            ref="textarea"
            v-model="input"
            rows="1"
            placeholder="问我任何学习问题，Enter 发送 / Shift + Enter 换行"
            :disabled="streaming"
            @input="onInput"
            @keydown.enter="onEnter"
          />
          <BaseButton v-if="streaming" variant="outline" @click="stop">
            <AppIcon name="stop" :size="15" />
            停止
          </BaseButton>
          <BaseButton v-else :disabled="!input.trim() && !images.length" @click="send">
            <AppIcon name="send" :size="15" />
            发送
          </BaseButton>
        </div>
        <p class="hint">
          AI 可能出错，请核验重要结论 · 已开启内容安全护栏与提示词注入防护
        </p>
        <input ref="fileInput" type="file" accept="image/*" multiple hidden @change="onFiles" />
      </footer>
    </section>
  </div>
</template>

<style scoped>
.tutor {
  display: grid;
  grid-template-columns: 246px 1fr;
  gap: 0;
  height: calc(100vh - var(--topbar-h));
}

.conv {
  border-right: 1px solid var(--border);
  background: var(--surface);
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.conv-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 16px 16px 12px;
}

.conv-head strong {
  font-size: 13px;
}

.conv-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.conv-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 11px;
  border-radius: var(--radius-sm);
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: all 0.18s ease;
}

.conv-item:hover {
  background: var(--surface-soft);
}

.conv-item.active {
  background: var(--brand-50);
  border-color: var(--brand-100);
}

.conv-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.conv-text .title {
  font-size: 12.8px;
  font-weight: 550;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-text .meta {
  font-size: 10.8px;
  color: var(--text-mute);
}

.del {
  opacity: 0;
  color: var(--text-mute);
  padding: 4px;
  border-radius: 6px;
  transition: all 0.18s ease;
}

.conv-item:hover .del {
  opacity: 1;
}

.del:hover {
  color: var(--danger-500);
  background: var(--surface);
}

.conv-empty {
  font-size: 12px;
  color: var(--text-mute);
  padding: 18px 10px;
  text-align: center;
  line-height: 1.7;
}

.chat {
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--bg);
}

.chat-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 22px;
  border-bottom: 1px solid var(--border);
  background: var(--surface);
}

.chat-title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.chat-title strong {
  font-size: 13.5px;
  max-width: 46vw;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-title span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  color: var(--text-mute);
}

.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-mute);
}

.live-dot.on {
  background: var(--success-500);
  animation: pulse-dot 1.2s ease-in-out infinite;
}

.switch {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}

.switch input {
  display: none;
}

.track {
  width: 38px;
  height: 21px;
  border-radius: 999px;
  background: var(--surface-mute);
  border: 1px solid var(--border-strong);
  position: relative;
  transition: all 0.2s ease;
}

.thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 15px;
  height: 15px;
  border-radius: 50%;
  background: var(--surface);
  box-shadow: var(--shadow-1);
  transition: transform 0.2s cubic-bezier(0.22, 0.8, 0.3, 1);
}

.switch input:checked + .track {
  background: var(--brand-500);
  border-color: var(--brand-500);
}

.switch input:checked + .track .thumb {
  transform: translateX(17px);
}

.switch-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11.5px;
  color: var(--text-soft);
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 26px 22px 10px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  scroll-behavior: smooth;
}

.loading {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.welcome {
  margin: auto;
  max-width: 620px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 0 40px;
}

.welcome-icon {
  width: 62px;
  height: 62px;
  border-radius: 20px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, var(--brand-500), var(--accent-500));
  color: #fff;
  box-shadow: 0 16px 34px rgba(79, 70, 229, 0.28);
  margin-bottom: 6px;
}

.welcome h2 {
  font-size: 21px;
}

.welcome p {
  font-size: 13.5px;
  color: var(--text-soft);
  line-height: 1.8;
  max-width: 460px;
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
  justify-content: center;
  margin-top: 10px;
}

.suggestion {
  padding: 8px 14px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--surface);
  font-size: 12.5px;
  color: var(--text-soft);
  cursor: pointer;
  transition: all 0.18s ease;
}

.suggestion:hover {
  border-color: var(--brand-300);
  color: var(--brand-600);
  transform: translateY(-1px);
  box-shadow: var(--shadow-1);
}

.msg {
  display: flex;
  gap: 11px;
  align-items: flex-start;
  animation: fade-up 0.3s cubic-bezier(0.22, 0.8, 0.3, 1) both;
}

.msg.user {
  flex-direction: row;
  justify-content: flex-end;
}

.avatar {
  width: 30px;
  height: 30px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  flex: none;
  margin-top: 2px;
}

.avatar.ai {
  background: linear-gradient(135deg, var(--brand-500), var(--accent-500));
  box-shadow: 0 6px 16px rgba(79, 70, 229, 0.28);
}

.avatar.user {
  border-radius: 50%;
}

.bubble-wrap {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-width: min(760px, 78%);
  min-width: 0;
}

.msg.user .bubble-wrap {
  align-items: flex-end;
}

.bubble {
  padding: 14px 16px;
  border-radius: var(--radius-lg);
  background: var(--surface);
  border: 1px solid var(--border);
  box-shadow: var(--shadow-1);
  min-width: 0;
  width: 100%;
}

.msg.user .bubble {
  background: linear-gradient(135deg, var(--brand-500), #6d5cf6);
  border: none;
  color: #fff;
  box-shadow: 0 10px 24px rgba(79, 70, 229, 0.24);
}

.bubble.error {
  border-color: #fecaca;
}

.msg.user .bubble.error {
  border-color: transparent;
}

.plain {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
  line-height: 1.7;
}

.msg-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.msg-images img {
  max-width: 190px;
  max-height: 150px;
  border-radius: 10px;
  object-fit: cover;
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.typing {
  display: inline-flex;
  gap: 4px;
  padding: 4px 0;
}

.typing span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--brand-300);
  animation: pulse-dot 1.2s ease-in-out infinite;
}

.typing span:nth-child(2) {
  animation-delay: 0.18s;
}

.typing span:nth-child(3) {
  animation-delay: 0.36s;
}

.msg-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 11px;
  color: var(--text-mute);
  padding-left: 2px;
}

.msg-actions button {
  border: none;
  background: transparent;
  color: var(--text-mute);
  cursor: pointer;
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 6px;
}

.msg-actions button:hover {
  color: var(--brand-600);
  background: var(--surface-mute);
}

.stamp {
  margin-left: auto;
  letter-spacing: 0.02em;
}

.composer {
  padding: 12px 22px 16px;
  border-top: 1px solid var(--border);
  background: var(--surface);
}

.previews {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.preview {
  position: relative;
}

.preview img {
  width: 62px;
  height: 62px;
  object-fit: cover;
  border-radius: 10px;
  border: 1px solid var(--border);
}

.preview button {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 19px;
  height: 19px;
  border-radius: 50%;
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--text-soft);
  display: grid;
  place-items: center;
  cursor: pointer;
}

.input-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.tool-btn {
  width: 38px;
  height: 38px;
  flex: none;
  border-radius: 11px;
  border: 1px solid var(--border);
  background: var(--surface-soft);
  color: var(--text-soft);
  display: grid;
  place-items: center;
  cursor: pointer;
  transition: all 0.18s ease;
}

.tool-btn:hover {
  color: var(--brand-600);
  border-color: var(--brand-300);
}

.tool-btn.recording {
  color: #fff;
  background: var(--danger-500);
  border-color: var(--danger-500);
  animation: pulse-dot 1.2s ease-in-out infinite;
}

textarea {
  flex: 1;
  min-height: 38px;
  max-height: 168px;
  resize: none;
  padding: 9px 14px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-strong);
  background: var(--surface-soft);
  line-height: 1.6;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
}

textarea:focus {
  outline: none;
  border-color: var(--brand-300);
  background: var(--surface);
  box-shadow: var(--ring);
}

.hint {
  margin-top: 8px;
  font-size: 10.8px;
  color: var(--text-mute);
  text-align: center;
}

.icon-btn {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-soft);
  cursor: pointer;
}

.icon-btn:hover {
  background: var(--surface-soft);
}

.mobile-only {
  display: none;
}

@media (max-width: 900px) {
  .tutor {
    grid-template-columns: 1fr;
  }
  .conv {
    position: fixed;
    top: var(--topbar-h);
    bottom: 0;
    left: 0;
    width: 260px;
    z-index: 50;
    transform: translateX(-100%);
    transition: transform 0.24s ease;
    box-shadow: var(--shadow-3);
  }
  .conv.open {
    transform: translateX(0);
  }
  .mobile-only {
    display: grid;
  }
  .messages {
    padding: 18px 14px 8px;
  }
  .composer {
    padding: 10px 14px 14px;
  }
  .bubble-wrap {
    max-width: 88%;
  }
  .chat-title strong {
    max-width: 40vw;
  }
}
</style>
