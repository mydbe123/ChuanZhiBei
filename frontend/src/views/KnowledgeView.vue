<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import BaseButton from '@/components/BaseButton.vue'
import BaseCard from '@/components/BaseCard.vue'
import BaseModal from '@/components/BaseModal.vue'
import EmptyState from '@/components/EmptyState.vue'
import { api } from '@/api/client'
import type { Citation, DocumentItem, KnowledgeOverview } from '@/api/types'
import { toast } from '@/stores/toast'
import { formatBytes, formatNumber, formatRelative } from '@/utils/format'

const overview = ref<KnowledgeOverview | null>(null)
const loading = ref(true)
const uploading = ref(false)
const dragging = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const query = ref('')
const searching = ref(false)
const results = ref<Citation[]>([])

const noteOpen = ref(false)
const note = reactive({ title: '', content: '' })
const savingNote = ref(false)

onMounted(load)

async function load() {
  loading.value = true
  try {
    overview.value = await api.get<KnowledgeOverview>('/knowledge/documents')
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function pick() {
  fileInput.value?.click()
}

function onSelect(event: Event) {
  const target = event.target as HTMLInputElement
  if (target.files?.length) {
    void upload(Array.from(target.files))
  }
  target.value = ''
}

function onDrop(event: DragEvent) {
  dragging.value = false
  if (event.dataTransfer?.files?.length) {
    void upload(Array.from(event.dataTransfer.files))
  }
}

async function upload(files: File[]) {
  if (uploading.value) return
  uploading.value = true
  let success = 0
  for (const file of files) {
    if (file.size > 25 * 1024 * 1024) {
      toast.error(`${file.name} 超过 25MB，已跳过`)
      continue
    }
    const form = new FormData()
    form.append('file', file)
    try {
      await api.upload<DocumentItem>('/knowledge/documents', form)
      success++
    } catch (e) {
      toast.error(`${file.name}：${(e as Error).message}`)
    }
  }
  uploading.value = false
  if (success) {
    toast.success(`已入库 ${success} 个文档，切片与向量已生成`)
    await load()
  }
}

async function createNote() {
  if (!note.title.trim() || note.content.trim().length < 30) {
    toast.error('标题必填，内容至少 30 字')
    return
  }
  savingNote.value = true
  try {
    await api.post('/knowledge/documents/text', { title: note.title.trim(), content: note.content })
    toast.success('笔记已入库')
    noteOpen.value = false
    note.title = ''
    note.content = ''
    await load()
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    savingNote.value = false
  }
}

async function remove(item: DocumentItem) {
  try {
    await api.del(`/knowledge/documents/${item.id}`)
    toast.success('文档已删除')
    await load()
  } catch (e) {
    toast.error((e as Error).message)
  }
}

async function search() {
  if (!query.value.trim()) return
  searching.value = true
  try {
    results.value = await api.post<Citation[]>('/knowledge/search', {
      query: query.value.trim(),
      topK: 5
    })
    if (!results.value.length) {
      toast.info('没有检索到相关片段，试试更具体的关键词')
    }
  } catch (e) {
    toast.error((e as Error).message)
  } finally {
    searching.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">
          <AppIcon name="knowledge" :size="22" />
          知识库
        </h1>
        <p class="page-sub">
          上传讲义、笔记或课件，系统会自动切片并生成向量索引，AI 导师回答时会带上原文出处。
        </p>
      </div>
      <BaseButton variant="outline" @click="noteOpen = true">
        <AppIcon name="plus" :size="15" />
        新建笔记
      </BaseButton>
    </div>

    <div class="grid grid-4 stats">
      <BaseCard>
        <span class="stat-label">文档</span>
        <strong class="stat-value">{{ overview?.documentCount ?? 0 }}</strong>
      </BaseCard>
      <BaseCard>
        <span class="stat-label">切片</span>
        <strong class="stat-value">{{ formatNumber(overview?.chunkCount ?? 0) }}</strong>
      </BaseCard>
      <BaseCard>
        <span class="stat-label">总字数</span>
        <strong class="stat-value">{{ formatNumber(overview?.charCount ?? 0) }}</strong>
      </BaseCard>
      <BaseCard>
        <span class="stat-label">检索方式</span>
        <strong class="stat-value small">混合检索 + MMR</strong>
      </BaseCard>
    </div>

    <div class="grid grid-2">
      <BaseCard>
        <h3>上传资料</h3>
        <p class="muted sub">支持 PDF / Markdown / TXT，单文件 ≤ 25MB</p>
        <div
          class="dropzone"
          :class="{ dragging, uploading }"
          @click="pick"
          @dragover.prevent="dragging = true"
          @dragleave.prevent="dragging = false"
          @drop.prevent="onDrop"
        >
          <div class="dz-icon">
            <AppIcon :name="uploading ? 'clock' : 'upload'" :size="22" />
          </div>
          <strong>{{ uploading ? '正在解析与向量化…' : '拖拽文件到这里，或点击选择' }}</strong>
          <span>解析 → 句子级切片 → 稀疏向量 → 写入索引</span>
        </div>
        <input
          ref="fileInput"
          type="file"
          accept=".pdf,.md,.markdown,.txt"
          multiple
          hidden
          @change="onSelect"
        />
      </BaseCard>

      <BaseCard>
        <h3>检索测试</h3>
        <p class="muted sub">验证索引效果，结果即为 AI 导师可引用的片段</p>
        <div class="search-row">
          <input
            v-model="query"
            placeholder="例如：B+ 树为什么适合范围查询"
            @keydown.enter="search"
          />
          <BaseButton :loading="searching" @click="search">
            <AppIcon name="search" :size="15" />
            检索
          </BaseButton>
        </div>
        <ul v-if="results.length" class="hits">
          <li v-for="(hit, index) in results" :key="index">
            <div class="hit-top">
              <span class="chip chip-brand">{{ hit.documentTitle }}</span>
              <span class="hit-seq">第 {{ hit.seq }} 段</span>
              <span class="score">{{ (hit.score * 100).toFixed(0) }}%</span>
            </div>
            <p>{{ hit.snippet }}</p>
          </li>
        </ul>
        <p v-else class="muted empty-hint">输入关键词后按 Enter，查看混合检索的命中片段</p>
      </BaseCard>
    </div>

    <BaseCard pad="none" class="list-card">
      <div class="list-head">
        <h3>我的知识库</h3>
        <span class="muted">{{ overview?.documentCount ?? 0 }} 篇</span>
      </div>

      <div v-if="loading" class="skeleton-list">
        <div class="skeleton" style="height: 58px" />
        <div class="skeleton" style="height: 58px" />
      </div>

      <ul v-else-if="overview?.documents.length" class="docs">
        <li v-for="doc in overview.documents" :key="doc.id">
          <span class="doc-icon"><AppIcon name="knowledge" :size="16" /></span>
          <div class="doc-main">
            <div class="doc-title">
              {{ doc.title }}
              <span v-if="doc.shared" class="chip">公共示例</span>
              <span class="chip chip-brand">{{ doc.sourceType }}</span>
            </div>
            <span class="doc-meta">
              {{ doc.chunkCount }} 个切片 · {{ formatBytes(doc.byteSize) }} · {{ formatRelative(doc.createdAt) }}
            </span>
          </div>
          <button v-if="!doc.shared" class="doc-del" type="button" @click="remove(doc)">
            <AppIcon name="trash" :size="15" />
          </button>
        </li>
      </ul>

      <EmptyState
        v-else
        title="知识库还是空的"
        description="上传课程资料，AI 导师就能基于你的材料回答问题，并给出来源引用"
      >
        <template #icon><AppIcon name="upload" :size="22" /></template>
        <BaseButton variant="soft" @click="pick">上传第一份资料</BaseButton>
      </EmptyState>
    </BaseCard>

    <BaseModal :open="noteOpen" title="新建知识笔记" width="560px" @close="noteOpen = false">
      <label class="field">
        <span>标题</span>
        <input v-model="note.title" placeholder="例如：红黑树插入调整规则" />
      </label>
      <label class="field">
        <span>内容</span>
        <textarea v-model="note.content" rows="8" placeholder="粘贴或输入你的学习笔记…" />
      </label>
      <template #footer>
        <BaseButton variant="ghost" @click="noteOpen = false">取消</BaseButton>
        <BaseButton :loading="savingNote" @click="createNote">保存并入库</BaseButton>
      </template>
    </BaseModal>
  </div>
</template>

<style scoped>
.stats {
  margin-bottom: 18px;
}

.stat-label {
  font-size: 11.5px;
  color: var(--text-mute);
  letter-spacing: 0.04em;
}

.stat-value {
  display: block;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
  margin-top: 4px;
}

.stat-value.small {
  font-size: 14px;
  padding-top: 8px;
}

h3 {
  font-size: 15px;
}

.sub {
  font-size: 12px;
  margin: 4px 0 14px;
}

.dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 30px 18px;
  border-radius: var(--radius-md);
  border: 1.6px dashed var(--border-strong);
  background: var(--surface-soft);
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: center;
}

.dropzone:hover,
.dropzone.dragging {
  border-color: var(--brand-300);
  background: var(--brand-50);
}

.dropzone.uploading {
  pointer-events: none;
  opacity: 0.8;
}

.dz-icon {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  background: var(--surface);
  color: var(--brand-500);
  box-shadow: var(--shadow-1);
  margin-bottom: 4px;
}

.dropzone strong {
  font-size: 13.5px;
}

.dropzone span {
  font-size: 11.5px;
  color: var(--text-mute);
}

.search-row {
  display: flex;
  gap: 9px;
}

.search-row input {
  flex: 1;
  height: 38px;
  padding: 0 13px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-strong);
  background: var(--surface-soft);
}

.search-row input:focus {
  outline: none;
  border-color: var(--brand-300);
  box-shadow: var(--ring);
  background: var(--surface);
}

.hits {
  list-style: none;
  margin: 14px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 9px;
  max-height: 260px;
  overflow-y: auto;
}

.hits li {
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  background: var(--surface-soft);
  border: 1px solid var(--border);
  animation: fade-up 0.25s ease both;
}

.hit-top {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.hit-seq {
  font-size: 11px;
  color: var(--text-mute);
}

.score {
  margin-left: auto;
  font-size: 11.5px;
  font-weight: 650;
  color: var(--success-500);
}

.hits p {
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--text-soft);
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.empty-hint {
  margin-top: 16px;
  font-size: 12px;
  text-align: center;
  line-height: 1.7;
}

.list-card {
  margin-top: 18px;
}

.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px 12px;
}

.docs {
  list-style: none;
  margin: 0;
  padding: 0;
}

.docs li {
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 13px 20px;
  border-top: 1px solid var(--border);
  transition: background 0.18s ease;
}

.docs li:hover {
  background: var(--surface-soft);
}

.doc-icon {
  width: 36px;
  height: 36px;
  border-radius: 11px;
  display: grid;
  place-items: center;
  background: var(--brand-50);
  color: var(--brand-500);
  flex: none;
}

.doc-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.doc-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
  font-weight: 560;
  flex-wrap: wrap;
}

.doc-meta {
  font-size: 11.5px;
  color: var(--text-mute);
}

.doc-del {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-mute);
  cursor: pointer;
  display: grid;
  place-items: center;
  transition: all 0.18s ease;
}

.doc-del:hover {
  color: var(--danger-500);
  background: var(--danger-100);
}

.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px 20px 20px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 14px;
}

.field span {
  font-size: 12px;
  font-weight: 550;
  color: var(--text-soft);
}

.field input,
.field textarea {
  padding: 10px 13px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-strong);
  background: var(--surface-soft);
  resize: vertical;
}

.field input:focus,
.field textarea:focus {
  outline: none;
  border-color: var(--brand-300);
  box-shadow: var(--ring);
  background: var(--surface);
}
</style>
