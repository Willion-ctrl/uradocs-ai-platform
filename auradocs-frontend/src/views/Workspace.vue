<template>
  <div class="workspace-layout">
    <!-- 顶部导航栏 -->
    <header class="top-bar">
      <div class="top-left">
        <span class="logo">AuraDocs</span>
        <el-divider direction="vertical" />
        <el-select
          v-model="currentSpaceId"
          placeholder="选择知识空间"
          style="width: 210px"
          @change="handleSpaceChange"
        >
          <el-option
            v-for="space in spaces"
            :key="space.id"
            :label="space.name"
            :value="space.id"
          />
        </el-select>
        <el-button type="primary" plain size="small" icon="Plus" @click="spaceDialogVisible = true">
          新建空间
        </el-button>
      </div>

      <div class="top-right">
        <span class="user-badge">{{ userStore.userInfo?.nickname || userStore.userInfo?.username }}</span>
        <el-button size="small" type="danger" plain @click="handleLogout">退出</el-button>
      </div>
    </header>

    <!-- 主体三栏 -->
    <div class="main-body">
      <!-- 左栏：搜索 + 文档目录 -->
      <aside class="left-sidebar">
        <div class="sidebar-header">
          <span>文档列表</span>
          <el-button
            type="primary"
            link
            icon="DocumentAdd"
            :disabled="!currentSpaceId"
            @click="handleCreateDoc"
          >
            新建文档
          </el-button>
        </div>

        <div class="sidebar-search">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索标题 / 正文 / AI摘要..."
            prefix-icon="Search"
            size="small"
            clearable
            :disabled="!currentSpaceId"
            @input="handleSearchDocs"
            @clear="handleSearchDocs"
          />
        </div>

        <el-scrollbar class="doc-list">
          <el-empty
            v-if="docs.length === 0"
            :description="searchKeyword ? '未找到匹配文档' : '暂无文档'"
            :image-size="60"
          />
          <div
            v-for="doc in docs"
            :key="doc.id"
            class="doc-item"
            :class="{ active: currentDoc?.id === doc.id }"
            @click="selectDoc(doc.id)"
          >
            <el-icon><Document /></el-icon>
            <span class="doc-title">{{ doc.title }}</span>
            <el-tag
              v-if="doc.aiSummary"
              size="small"
              type="primary"
              effect="plain"
              class="summary-dot"
            >
              AI
            </el-tag>
            <el-icon class="doc-delete" @click.stop="handleDeleteDoc(doc)"><Delete /></el-icon>
          </div>
        </el-scrollbar>
      </aside>

      <!-- 中栏：Markdown 编辑器 + AI 摘要横幅 -->
      <main class="center-editor" @keydown.ctrl.s.prevent="handleSaveDoc">
        <template v-if="currentDoc">
          <div class="editor-toolbar">
            <el-input
              v-model="editorForm.title"
              placeholder="请输入文档标题"
              class="title-input"
            />
            <div class="toolbar-actions">
              <el-radio-group v-model="viewMode" size="small">
                <el-radio-button value="edit">编辑</el-radio-button>
                <el-radio-button value="preview">预览</el-radio-button>
              </el-radio-group>
              <el-button
                type="success"
                plain
                size="small"
                :loading="summarizing"
                icon="MagicStick"
                @click="handleGenerateSummary"
              >
                {{ currentDoc.aiSummary ? '重新生成摘要' : 'AI 摘要' }}
              </el-button>
              <el-button
                type="primary"
                size="small"
                :loading="saving"
                icon="Check"
                @click="handleSaveDoc"
              >
                保存
              </el-button>
            </div>
          </div>

          <!-- AI 摘要展示横幅 -->
          <div v-if="currentDoc.aiSummary" class="ai-summary-banner">
            <div class="summary-label">
              <el-icon><MagicStick /></el-icon>
              <span>AI 智能摘要</span>
            </div>
            <p class="summary-text">{{ currentDoc.aiSummary }}</p>
          </div>

          <div class="editor-content">
            <textarea
              v-if="viewMode === 'edit'"
              v-model="editorForm.contentMd"
              class="md-textarea"
              placeholder="支持 Markdown 语法，按 Ctrl + S 快速保存..."
            />
            <div
              v-else
              class="md-preview"
              v-html="renderedMarkdown"
            />
          </div>
        </template>
        <div v-else class="empty-editor">
          <el-empty description="请在左侧选择或新建一篇文档" />
        </div>
      </main>

      <!-- 右栏：AI 协作侧边栏 -->
      <aside class="right-ai-panel">
        <div class="ai-header">
          <div class="ai-title">
            <el-icon color="#4f46e5"><MagicStick /></el-icon>
            <span>AI 知识助手</span>
          </div>
          <el-button link size="small" @click="resetChat">新对话</el-button>
        </div>

        <div class="ai-context-bar">
          <el-tag v-if="currentDoc" size="small" type="success" effect="plain">
            已关联文档：{{ currentDoc.title }}
          </el-tag>
          <el-tag v-else size="small" type="info" effect="plain">
            通用对话模式（未选中文档）
          </el-tag>
        </div>

        <el-scrollbar ref="chatScrollRef" class="chat-messages">
          <div v-if="chatMessages.length === 0" class="chat-welcome">
            你可以直接针对当前文档提问，例如：“总结这篇文档的核心要点”。
          </div>
          <div
            v-for="(msg, idx) in chatMessages"
            :key="idx"
            class="chat-bubble-wrap"
            :class="msg.role"
          >
            <div class="chat-bubble">
              <div v-if="msg.role === 'user'">{{ msg.content }}</div>
              <div v-else class="ai-md" v-html="renderMd(msg.content || '思考中...')" />
            </div>
          </div>
        </el-scrollbar>

        <div class="chat-input-box">
          <el-input
            v-model="questionInput"
            type="textarea"
            :rows="2"
            resize="none"
            placeholder="输入问题，按 Enter 发送..."
            :disabled="aiStreaming"
            @keydown.enter.exact.prevent="handleSendQuestion"
          />
          <el-button
            type="primary"
            class="send-btn"
            :loading="aiStreaming"
            @click="handleSendQuestion"
          >
            发送
          </el-button>
        </div>
      </aside>
    </div>

    <!-- 新建知识空间弹窗 -->
    <el-dialog v-model="spaceDialogVisible" title="新建知识空间" width="420px">
      <el-form :model="spaceForm" label-width="80px">
        <el-form-item label="空间名称" required>
          <el-input v-model="spaceForm.name" placeholder="例如：后端架构规范库" />
        </el-form-item>
        <el-form-item label="空间描述">
          <el-input v-model="spaceForm.description" type="textarea" placeholder="选填" />
        </el-form-item>
        <el-form-item label="可见性">
          <el-radio-group v-model="spaceForm.visibility">
            <el-radio :value="0">仅自己可见</el-radio>
            <el-radio :value="1">团队公开</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="spaceDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateSpace">确定创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import { useUserStore } from '@/stores/user'
import {
  listSpacesApi,
  createSpaceApi,
  listDocsApi,
  getDocApi,
  createDocApi,
  updateDocApi,
  deleteDocApi,
  generateDocSummaryApi,
  searchDocsApi
} from '@/api/kb'
import { streamAiChat } from '@/api/ai'

const router = useRouter()
const userStore = useUserStore()

// ===== 空间与文档状态 =====
const spaces = ref([])
const currentSpaceId = ref(null)
const docs = ref([])
const currentDoc = ref(null)
const viewMode = ref('edit')
const saving = ref(false)
const summarizing = ref(false)

// 防抖搜索状态
const searchKeyword = ref('')
let searchTimer = null

const editorForm = reactive({
  title: '',
  contentMd: ''
})

// 新建空间弹窗
const spaceDialogVisible = ref(false)
const spaceForm = reactive({ name: '', description: '', visibility: 0 })

// ===== AI 对话状态 =====
const chatMessages = ref([])
const questionInput = ref('')
const aiStreaming = ref(false)
const sessionId = ref('')
const chatScrollRef = ref(null)

const renderMd = (text) => marked.parse(text || '')
const renderedMarkdown = computed(() => renderMd(editorForm.contentMd))

onMounted(async () => {
  await userStore.fetchUserInfo()
  await loadSpaces()
})

const loadSpaces = async () => {
  spaces.value = await listSpacesApi()
  if (spaces.value.length > 0 && !currentSpaceId.value) {
    currentSpaceId.value = spaces.value[0].id
    await loadDocs(currentSpaceId.value)
  }
}

const handleSpaceChange = async (spaceId) => {
  searchKeyword.value = ''
  currentDoc.value = null
  await loadDocs(spaceId)
}

const handleCreateSpace = async () => {
  if (!spaceForm.name.trim()) {
    return ElMessage.warning('请输入空间名称')
  }
  const created = await createSpaceApi(spaceForm)
  ElMessage.success('空间创建成功')
  spaceDialogVisible.value = false
  spaceForm.name = ''
  spaceForm.description = ''
  spaceForm.visibility = 0
  await loadSpaces()
  currentSpaceId.value = created.id
  await handleSpaceChange(created.id)
}

const loadDocs = async (spaceId) => {
  docs.value = await listDocsApi(spaceId)
  if (docs.value.length > 0) {
    await selectDoc(docs.value[0].id)
  } else {
    currentDoc.value = null
  }
}

const selectDoc = async (docId) => {
  const detail = await getDocApi(docId)
  currentDoc.value = detail
  editorForm.title = detail.title
  editorForm.contentMd = detail.contentMd || ''
}

const handleCreateDoc = async () => {
  const created = await createDocApi({
    spaceId: currentSpaceId.value,
    title: '未命名文档',
    contentMd: '# 新文档\n\n在这里开始记录你的知识...'
  })
  ElMessage.success('文档已创建')
  searchKeyword.value = ''
  await loadDocs(currentSpaceId.value)
  await selectDoc(created.id)
  viewMode.value = 'edit'
}

const handleSaveDoc = async (silent = false) => {
  if (!currentDoc.value) return false
  if (!editorForm.title.trim()) {
    ElMessage.warning('文档标题不能为空')
    return false
  }
  saving.value = true
  try {
    const updated = await updateDocApi(currentDoc.value.id, {
      spaceId: currentDoc.value.spaceId,
      title: editorForm.title,
      contentMd: editorForm.contentMd,
      status: 1
    })
    currentDoc.value = updated
    const target = docs.value.find((d) => d.id === updated.id)
    if (target) target.title = updated.title
    if (!silent) ElMessage.success('保存成功')
    return true
  } finally {
    saving.value = false
  }
}

const handleDeleteDoc = async (doc) => {
  await ElMessageBox.confirm(`确认删除文档「${doc.title}」吗？`, '提示', {
    type: 'warning'
  })
  await deleteDocApi(doc.id)
  ElMessage.success('已删除')
  if (currentDoc.value?.id === doc.id) {
    currentDoc.value = null
  }
  if (searchKeyword.value.trim()) {
    docs.value = await searchDocsApi(currentSpaceId.value, searchKeyword.value)
  } else {
    await loadDocs(currentSpaceId.value)
  }
}

// ===== 防抖多字段搜索 =====
const handleSearchDocs = () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(async () => {
    if (!currentSpaceId.value) return
    const kw = searchKeyword.value.trim()
    docs.value = kw
      ? await searchDocsApi(currentSpaceId.value, kw)
      : await listDocsApi(currentSpaceId.value)
  }, 300)
}

// ===== 一键生成 AI 摘要（未保存时自动先保存） =====
const handleGenerateSummary = async () => {
  if (!currentDoc.value) return
  if (!editorForm.contentMd.trim()) {
    return ElMessage.warning('请先填写文档正文再生成摘要')
  }

  summarizing.value = true
  try {
    // 若本地编辑内容与已保存内容不一致，先静默保存再调用 AI
    const hasUnsavedChanges =
      editorForm.title !== currentDoc.value.title ||
      editorForm.contentMd !== (currentDoc.value.contentMd || '')
    if (hasUnsavedChanges) {
      const ok = await handleSaveDoc(true)
      if (!ok) return
    }

    const updated = await generateDocSummaryApi(currentDoc.value.id)
    currentDoc.value.aiSummary = updated.aiSummary
    const target = docs.value.find((d) => d.id === updated.id)
    if (target) target.aiSummary = updated.aiSummary
    ElMessage.success('AI 摘要已生成并保存')
  } finally {
    summarizing.value = false
  }
}

// ===== AI 流式问答逻辑 =====
const scrollToBottom = async () => {
  await nextTick()
  const wrap = chatScrollRef.value?.wrapRef
  if (wrap) wrap.scrollTop = wrap.scrollHeight
}

const resetChat = () => {
  chatMessages.value = []
  sessionId.value = ''
}

const handleSendQuestion = async () => {
  const q = questionInput.value.trim()
  if (!q || aiStreaming.value) return

  chatMessages.value.push({ role: 'user', content: q })
  const aiMsg = reactive({ role: 'assistant', content: '' })
  chatMessages.value.push(aiMsg)
  questionInput.value = ''
  aiStreaming.value = true
  await scrollToBottom()

  await streamAiChat(
    {
      sessionId: sessionId.value || undefined,
      docId: currentDoc.value?.id || undefined,
      question: q
    },
    {
      onMeta: (meta) => {
        if (meta.sessionId) sessionId.value = meta.sessionId
      },
      onMessage: (chunk) => {
        aiMsg.content += chunk
        scrollToBottom()
      },
      onDone: () => {
        aiStreaming.value = false
      },
      onError: (err) => {
        aiStreaming.value = false
        ElMessage.error(err.message || 'AI 对话异常')
        if (!aiMsg.content) aiMsg.content = '（请求失败，请检查后端 AI 配置）'
      }
    }
  )
}

const handleLogout = () => {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.workspace-layout {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f8fafc;
}

.top-bar {
  height: 54px;
  padding: 0 20px;
  background: #ffffff;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.top-left, .top-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo {
  font-size: 18px;
  font-weight: 700;
  color: #312e81;
}

.user-badge {
  font-size: 14px;
  color: #475569;
}

.main-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* 左栏 */
.left-sidebar {
  width: 250px;
  background: #ffffff;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 12px 14px;
  border-bottom: 1px solid #f1f5f9;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}

.sidebar-search {
  padding: 8px 10px;
  border-bottom: 1px solid #f1f5f9;
}

.doc-list {
  flex: 1;
  padding: 8px;
}

.doc-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  color: #334155;
  margin-bottom: 4px;
}

.doc-item:hover {
  background: #f1f5f9;
}

.doc-item.active {
  background: #eef2ff;
  color: #4338ca;
  font-weight: 500;
}

.doc-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-dot {
  height: 18px;
  padding: 0 5px;
  font-size: 10px;
}

.doc-delete {
  opacity: 0;
  color: #ef4444;
}

.doc-item:hover .doc-delete {
  opacity: 1;
}

/* 中栏 */
.center-editor {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  min-width: 0;
}

.editor-toolbar {
  padding: 10px 20px;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  gap: 16px;
}

.title-input {
  flex: 1;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ai-summary-banner {
  margin: 12px 20px 0;
  padding: 12px 16px;
  background: linear-gradient(90deg, #eef2ff 0%, #f5f3ff 100%);
  border: 1px solid #c7d2fe;
  border-radius: 8px;
}

.summary-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #4338ca;
  margin-bottom: 4px;
}

.summary-text {
  font-size: 13px;
  line-height: 1.6;
  color: #334155;
}

.editor-content {
  flex: 1;
  overflow: hidden;
  display: flex;
}

.md-textarea {
  flex: 1;
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  padding: 20px 24px;
  font-size: 15px;
  line-height: 1.75;
  font-family: 'Consolas', 'Monaco', monospace;
  color: #1e293b;
}

.md-preview {
  flex: 1;
  overflow-y: auto;
  padding: 20px 28px;
  line-height: 1.8;
  color: #1e293b;
}

.empty-editor {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 右栏 AI */
.right-ai-panel {
  width: 360px;
  background: #f8fafc;
  border-left: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
}

.ai-header {
  padding: 12px 16px;
  background: #ffffff;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.ai-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 14px;
}

.ai-context-bar {
  padding: 8px 14px;
  background: #f1f5f9;
  border-bottom: 1px solid #e2e8f0;
}

.chat-messages {
  flex: 1;
  padding: 14px;
}

.chat-welcome {
  font-size: 13px;
  color: #64748b;
  text-align: center;
  margin-top: 30px;
  line-height: 1.6;
}

.chat-bubble-wrap {
  display: flex;
  margin-bottom: 12px;
}

.chat-bubble-wrap.user {
  justify-content: flex-end;
}

.chat-bubble {
  max-width: 88%;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.chat-bubble-wrap.user .chat-bubble {
  background: #4f46e5;
  color: #ffffff;
}

.chat-bubble-wrap.assistant .chat-bubble {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  color: #1e293b;
}

.chat-input-box {
  padding: 12px;
  background: #ffffff;
  border-top: 1px solid #e2e8f0;
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.send-btn {
  height: 52px;
}
</style>