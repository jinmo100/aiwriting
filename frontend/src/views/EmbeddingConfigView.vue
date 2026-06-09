<template>
  <div class="embedding-config-view">
    <el-card class="section-card">
      <template #header>
        <div class="card-header">
          <span>Embedding 配置</span>
          <el-button type="primary" @click="openCreate">新增配置</el-button>
        </div>
      </template>

      <el-alert
        class="mb-16"
        type="info"
        :closable="false"
        title="Embedding 用于构建个人知识索引；V1 固定使用 1536 维向量，不会参与作文评分。"
      />

      <el-table :data="configs" v-loading="loading">
        <el-table-column prop="configName" label="配置名称" min-width="150" />
        <el-table-column prop="modelName" label="模型" min-width="180" />
        <el-table-column label="API Key" width="140">
          <template #default="{ row }">
            <span v-if="row.hasApiKey">{{ row.apiKeyPreview || '已保存' }}</span>
            <span v-else class="muted">未保存</span>
          </template>
        </el-table-column>
        <el-table-column label="维度" width="90">
          <template #default="{ row }">{{ row.dimensions }}</template>
        </el-table-column>
        <el-table-column label="测试状态" min-width="160">
          <template #default="{ row }">
            <el-tag v-if="row.lastTestStatus" :type="row.lastTestStatus === 'SUCCESS' ? 'success' : 'danger'">
              {{ row.lastTestStatus === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
            <span v-else class="muted">未测试</span>
            <div v-if="row.lastTestMessage" class="test-message">{{ row.lastTestMessage }}</div>
          </template>
        </el-table-column>
        <el-table-column label="默认" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault" type="success">默认</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="320">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="primary" @click="testSaved(row)">测试</el-button>
            <el-button v-if="!row.isDefault" size="small" type="warning" @click="setDefault(row)">设默认</el-button>
            <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="section-card">
      <template #header>
        <div class="card-header">
          <span>知识索引状态</span>
          <el-button
            type="primary"
            :disabled="indexActive || !defaultConfig"
            :loading="buildingIndex"
            @click="buildIndex"
          >
            构建我的知识索引
          </el-button>
        </div>
      </template>
      <div v-if="!defaultConfig" class="muted">请先创建并设置默认 Embedding 配置。</div>
      <div v-else class="overview-grid">
        <div><span class="muted">默认配置：</span>{{ defaultConfig.configName }}</div>
        <div><span class="muted">状态：</span>{{ indexStatusLabel }}</div>
        <div><span class="muted">已索引片段：</span>{{ indexStatus?.indexedChunks || 0 }}</div>
        <div><span class="muted">构建进度：</span>{{ indexProgressLabel }}</div>
        <div v-if="indexStatus?.embeddingVersion"><span class="muted">版本：</span>{{ indexStatus.embeddingVersion }}</div>
        <div v-if="indexStatus?.errorMessage"><span class="muted">失败信息：</span>{{ indexStatus.errorMessage }}</div>
      </div>
      <el-progress
        v-if="defaultConfig && indexProgressPercent !== null"
        class="index-progress"
        :percentage="indexProgressPercent"
        :status="indexStatus?.status === 'FAILED' ? 'exception' : indexStatus?.status === 'COMPLETED' ? 'success' : undefined"
        :indeterminate="indexActive && indexProgressPercent === 0"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑 Embedding 配置' : '新增 Embedding 配置'" width="680px">
      <el-form :model="form" label-width="130px">
        <el-form-item label="配置名称" required>
          <el-input v-model="form.configName" placeholder="例如：OpenAI Embedding" />
        </el-form-item>
        <el-form-item label="Base URL" required>
          <el-input v-model="form.baseUrl" placeholder="例如 https://api.openai.com/v1" />
        </el-form-item>
        <el-form-item label="API Key" :required="!editingId">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            :placeholder="editingId ? '留空表示保留旧 Key' : '输入 Embedding API Key'"
          />
          <div v-if="editingConfig?.apiKeyPreview" class="hint">已保存：{{ editingConfig.apiKeyPreview }}</div>
        </el-form-item>
        <el-form-item label="模型名称" required>
          <el-input v-model="form.modelName" placeholder="text-embedding-3-large" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="维度">
              <el-input-number v-model="form.dimensions" :min="1536" :max="1536" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Timeout(s)">
              <el-input-number v-model="form.timeoutSeconds" :min="1" :max="300" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="testUnsaved" :loading="testing">测试连接</el-button>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { EmbeddingConfig, EmbeddingConfigRequest, RagIndexStatus } from '@/types'
import {
  createEmbeddingConfig,
  deleteEmbeddingConfig,
  getEmbeddingConfigs,
  setDefaultEmbeddingConfig,
  testSavedEmbeddingConfig,
  testUnsavedEmbeddingConfig,
  updateEmbeddingConfig
} from '@/api/embedding'
import { getMyRagIndexStatus, rebuildMyRagIndex } from '@/api/rag'

const configs = ref<EmbeddingConfig[]>([])
const indexStatus = ref<RagIndexStatus | null>(null)
const loading = ref(false)
const dialogVisible = ref(false)
const submitting = ref(false)
const testing = ref(false)
const buildingIndex = ref(false)
const editingId = ref<number | null>(null)
const editingConfig = ref<EmbeddingConfig | null>(null)
let indexPollingTimer: number | undefined

const form = ref<EmbeddingConfigRequest>({
  configName: '',
  providerType: 'OPENAI_EMBEDDINGS',
  baseUrl: '',
  apiKey: '',
  modelName: 'text-embedding-3-large',
  dimensions: 1536,
  timeoutSeconds: 60,
  isDefault: false
})

const defaultConfig = computed(() => configs.value.find((item) => item.isDefault) || null)
const indexActive = computed(() => ['PENDING', 'RUNNING'].includes(indexStatus.value?.status || ''))
const indexStatusLabel = computed(() => {
  const status = indexStatus.value?.status
  if (status === 'PENDING') return '等待构建'
  if (status === 'RUNNING') return '构建中'
  if (status === 'COMPLETED') return '已完成'
  if (status === 'FAILED') return '失败'
  if (status === 'SKIPPED') return '已跳过'
  return indexStatus.value?.message || '未构建'
})
const indexProgress = computed(() => {
  if (!indexStatus.value?.resultJson) return null
  try {
    return JSON.parse(indexStatus.value.resultJson) as {
      totalChunks?: number
      processedChunks?: number
      failedChunks?: number
    }
  } catch {
    return null
  }
})
const indexProgressLabel = computed(() => {
  if (indexActive.value) return '构建中'
  if (!indexProgress.value) return '暂无进度'
  const total = indexProgress.value.totalChunks ?? 0
  const processed = indexProgress.value.processedChunks ?? 0
  const failed = indexProgress.value.failedChunks ?? 0
  return `${processed}/${total}（失败 ${failed}）`
})
const indexProgressPercent = computed(() => {
  if (indexActive.value) return 0
  const total = indexProgress.value?.totalChunks
  if (!total) {
    return indexStatus.value?.indexedChunks ? 100 : null
  }
  return Math.min(100, Math.round(((indexProgress.value?.processedChunks || 0) / total) * 100))
})

onMounted(async () => {
  await loadConfigs()
  await loadIndexStatus()
})

onUnmounted(() => {
  stopIndexPolling()
})

async function loadConfigs() {
  loading.value = true
  try {
    configs.value = await getEmbeddingConfigs()
  } finally {
    loading.value = false
  }
}

async function loadIndexStatus() {
  indexStatus.value = await getMyRagIndexStatus(defaultConfig.value?.id)
  if (indexActive.value) {
    startIndexPolling()
  } else {
    stopIndexPolling()
  }
}

function openCreate() {
  editingId.value = null
  editingConfig.value = null
  form.value = {
    configName: '',
    providerType: 'OPENAI_EMBEDDINGS',
    baseUrl: '',
    apiKey: '',
    modelName: 'text-embedding-3-large',
    dimensions: 1536,
    timeoutSeconds: 60,
    isDefault: configs.value.length === 0
  }
  dialogVisible.value = true
}

function openEdit(row: EmbeddingConfig) {
  editingId.value = row.id
  editingConfig.value = row
  form.value = {
    configName: row.configName,
    providerType: row.providerType,
    baseUrl: row.baseUrl,
    apiKey: '',
    modelName: row.modelName,
    dimensions: row.dimensions,
    timeoutSeconds: row.timeoutSeconds || 60,
    isDefault: row.isDefault
  }
  dialogVisible.value = true
}

function validate(requireKey = !editingId.value) {
  if (!form.value.configName || !form.value.baseUrl || !form.value.modelName) {
    ElMessage.warning('请填写配置名称、Base URL 和模型名称')
    return false
  }
  if (requireKey && !form.value.apiKey) {
    ElMessage.warning('请填写 API Key')
    return false
  }
  if (form.value.dimensions !== 1536) {
    ElMessage.warning('V1 仅支持 1536 维 Embedding')
    return false
  }
  return true
}

async function submit() {
  if (!validate()) return
  submitting.value = true
  try {
    if (editingId.value) {
      await updateEmbeddingConfig(editingId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await createEmbeddingConfig(form.value)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadConfigs()
    await loadIndexStatus()
  } finally {
    submitting.value = false
  }
}

async function testUnsaved() {
  if (!validate(true)) return
  testing.value = true
  try {
    const result = editingId.value && !form.value.apiKey
      ? await testSavedEmbeddingConfig(editingId.value)
      : await testUnsavedEmbeddingConfig(form.value)
    ElMessage[result.success ? 'success' : 'error'](result.message)
  } finally {
    testing.value = false
  }
}

async function testSaved(row: EmbeddingConfig) {
  const result = await testSavedEmbeddingConfig(row.id)
  ElMessage[result.success ? 'success' : 'error'](result.message)
  await loadConfigs()
}

async function setDefault(row: EmbeddingConfig) {
  await setDefaultEmbeddingConfig(row.id)
  ElMessage.success('已设为默认')
  await loadConfigs()
  await loadIndexStatus()
}

async function remove(row: EmbeddingConfig) {
  await ElMessageBox.confirm(`确定删除 Embedding 配置「${row.configName}」吗？`, '确认删除', { type: 'warning' })
  await deleteEmbeddingConfig(row.id)
  ElMessage.success('删除成功')
  await loadConfigs()
  await loadIndexStatus()
}

async function buildIndex() {
  buildingIndex.value = true
  try {
    indexStatus.value = await rebuildMyRagIndex(defaultConfig.value?.id, true)
    ElMessage.success('知识索引任务已提交')
    if (indexActive.value) {
      startIndexPolling()
    }
  } finally {
    buildingIndex.value = false
  }
}

function startIndexPolling() {
  if (indexPollingTimer) return
  indexPollingTimer = window.setInterval(() => {
    loadIndexStatus()
  }, 3000)
}

function stopIndexPolling() {
  if (indexPollingTimer) {
    window.clearInterval(indexPollingTimer)
    indexPollingTimer = undefined
  }
}
</script>

<style scoped>
.embedding-config-view {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}
.section-card {
  margin-bottom: 20px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.mb-16 {
  margin-bottom: 16px;
}
.muted,
.hint {
  color: #909399;
}
.test-message {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}
.overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px 20px;
  line-height: 1.8;
}
.index-progress {
  margin-top: 14px;
}
</style>
