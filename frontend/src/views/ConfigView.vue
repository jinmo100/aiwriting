<template>
  <div class="config-view">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>API配置管理</span>
          <el-button type="primary" @click="handleCreate">
            <el-icon><Plus /></el-icon>
            新增配置
          </el-button>
        </div>
      </template>

      <el-table :data="configStore.configs" v-loading="configStore.loading">
        <el-table-column prop="configName" label="配置名称" min-width="150" />
        <el-table-column label="Provider" min-width="170">
          <template #default="{ row }">
            <div>{{ providerDisplayName(row.providerType) }}</div>
            <small v-if="row.providerLabel" class="muted">{{ row.providerLabel }}</small>
          </template>
        </el-table-column>
        <el-table-column prop="modelName" label="模型名称" min-width="180" />
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
        <el-table-column label="操作" width="360">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="primary" @click="handleTestSaved(row)">测试</el-button>
            <el-button size="small" type="info" @click="handleFetchModelsSaved(row)">模型</el-button>
            <el-button size="small" type="warning" @click="handleSetDefault(row)" v-if="!row.isDefault">
              设为默认
            </el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showDialog" :title="editId ? '编辑配置' : '新增配置'" width="760px">
      <el-form :model="form" label-width="140px">
        <el-form-item label="配置名称" required>
          <el-input v-model="form.configName" placeholder="例如：晚霞公益站" />
        </el-form-item>

        <el-form-item label="提供商类型" required>
          <el-select v-model="form.providerType" placeholder="选择提供商类型" class="full-width">
            <el-option
              v-for="item in providerOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <div class="hint">{{ currentProviderHint }}</div>
        </el-form-item>

        <el-form-item label="提供商名称">
          <el-input v-model="form.providerLabel" placeholder="例如：OpenRouter、晚霞公益站、Anthropic" />
        </el-form-item>

        <el-form-item label="API Base URL" required>
          <el-input v-model="form.baseUrl" placeholder="请填写 API 根地址，不要包含具体 endpoint" />
          <div class="hint">{{ baseUrlHint }}</div>
        </el-form-item>

        <el-form-item :label="editId ? 'API Key' : 'API Key'" :required="!editId">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            :placeholder="editId ? '留空表示不修改，填写新 Key 表示替换' : '输入 API Key'"
          />
          <div v-if="editingConfig?.apiKeyPreview" class="hint">已保存：{{ editingConfig.apiKeyPreview }}</div>
          <el-button
            v-if="editId && allowApiKeyReveal"
            class="mt-8"
            size="small"
            @click="handleRevealKey"
            :loading="revealingKey"
          >
            查看完整 Key
          </el-button>
        </el-form-item>

        <el-form-item label="模型名称" required>
          <div class="model-row">
            <el-select
              v-model="form.modelName"
              filterable
              allow-create
              default-first-option
              placeholder="选择或输入模型名称"
              class="model-select"
            >
              <el-option
                v-for="model in fetchedModels"
                :key="model.id"
                :label="model.displayName ? `${model.id} - ${model.displayName}` : model.id"
                :value="model.id"
              />
            </el-select>
            <el-button @click="handleFetchModels(false)" :loading="fetchingModels">获取模型列表</el-button>
            <el-button @click="handleFetchModels(true)" :loading="fetchingModels">刷新</el-button>
          </div>
        </el-form-item>

        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="Temperature">
              <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="Max Tokens">
              <el-input-number v-model="form.maxTokens" :min="1" :max="200000" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="Timeout(s)">
              <el-input-number v-model="form.timeoutSeconds" :min="1" :max="300" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="高级参数 JSON">
          <el-input
            v-model="form.modelParametersJson"
            type="textarea"
            :rows="4"
            placeholder='例如：{"top_p":0.9}'
          />
        </el-form-item>

        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="handleTestConnection" :loading="testingConnection">测试连接</el-button>
          <el-button @click="handleTestStructured" :loading="testingStructured">测试输出格式</el-button>
          <el-button @click="showDialog = false">取消</el-button>
          <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useConfigStore } from '@/stores/useConfigStore'
import {
  createConfig,
  getConfigSecurityPolicy,
  updateConfig,
  deleteConfig,
  setDefaultConfig,
  revealApiKey,
  fetchModels,
  fetchModelsByConfig,
  testConnection,
  testConnectionByConfig,
  testStructuredOutput,
  testStructuredOutputByConfig
} from '@/api/config'
import type { ApiConfig, ApiConfigRequest, ProviderModelInfo, ProviderTestRequest, ProviderType } from '@/types'

const configStore = useConfigStore()
const showDialog = ref(false)
const submitting = ref(false)
const editId = ref<number | null>(null)
const editingConfig = ref<ApiConfig | null>(null)
const fetchedModels = ref<ProviderModelInfo[]>([])
const fetchingModels = ref(false)
const testingConnection = ref(false)
const testingStructured = ref(false)
const revealingKey = ref(false)
const allowApiKeyReveal = ref(false)

const providerOptions: Array<{ label: string; value: ProviderType; hint: string; endpointHint: string }> = [
  {
    label: 'OpenAI / OpenAI-compatible',
    value: 'OPENAI_CHAT_COMPLETIONS',
    hint: '适用于 OpenAI、OpenRouter、DeepSeek、New API 等 Chat Completions 兼容服务。',
    endpointHint: '请输入 API 根地址，例如 https://api.openai.com/v1，不要包含 /chat/completions。'
  },
  {
    label: 'OpenAI Responses',
    value: 'OPENAI_RESPONSES',
    hint: '适用于 OpenAI Responses API。',
    endpointHint: '请输入 API 根地址，例如 https://api.openai.com/v1，不要包含 /responses。'
  },
  {
    label: 'Anthropic Claude',
    value: 'ANTHROPIC_MESSAGES',
    hint: '适用于 Claude Messages 原生协议。',
    endpointHint: '请输入 API 根地址，例如 https://api.anthropic.com/v1，不要包含 /messages。'
  },
  {
    label: 'Google Gemini',
    value: 'GEMINI_GENERATE_CONTENT',
    hint: '适用于 Gemini generateContent 原生协议。',
    endpointHint: '请输入 API 根地址，例如 https://generativelanguage.googleapis.com/v1beta，不要包含 /models/{model}:generateContent。'
  }
]

const form = ref<ApiConfigRequest>({
  configName: '',
  providerType: 'OPENAI_CHAT_COMPLETIONS',
  providerLabel: '',
  baseUrl: '',
  apiKey: '',
  modelName: '',
  temperature: 0.3,
  maxTokens: 2048,
  timeoutSeconds: 60,
  modelParametersJson: '',
  isDefault: false
})

const currentProviderOption = computed(() => providerOptions.find(item => item.value === form.value.providerType) || providerOptions[0])
const currentProviderHint = computed(() => currentProviderOption.value.hint)
const baseUrlHint = computed(() => currentProviderOption.value.endpointHint)

onMounted(() => {
  configStore.loadConfigs()
  loadSecurityPolicy()
})

async function loadSecurityPolicy() {
  try {
    const policy = await getConfigSecurityPolicy()
    allowApiKeyReveal.value = Boolean(policy.allowApiKeyReveal)
  } catch {
    allowApiKeyReveal.value = false
  }
}

function providerDisplayName(providerType: ProviderType) {
  return providerOptions.find(item => item.value === providerType)?.label || providerType
}

function handleCreate() {
  editId.value = null
  editingConfig.value = null
  fetchedModels.value = []
  form.value = {
    configName: '',
    providerType: 'OPENAI_CHAT_COMPLETIONS',
    providerLabel: '',
    baseUrl: '',
    apiKey: '',
    modelName: '',
    temperature: 0.3,
    maxTokens: 2048,
    timeoutSeconds: 60,
    modelParametersJson: '',
    isDefault: false
  }
  showDialog.value = true
}

function handleEdit(row: ApiConfig) {
  editId.value = row.id
  editingConfig.value = row
  fetchedModels.value = []
  form.value = {
    configName: row.configName,
    providerType: row.providerType,
    providerLabel: row.providerLabel || '',
    baseUrl: row.baseUrl,
    apiKey: '',
    modelName: row.modelName,
    temperature: row.temperature ?? 0.3,
    maxTokens: row.maxTokens ?? 2048,
    timeoutSeconds: row.timeoutSeconds ?? 60,
    modelParametersJson: row.modelParametersJson || '',
    isDefault: row.isDefault
  }
  showDialog.value = true
}

function validateForm(requireApiKey = !editId.value) {
  if (!form.value.configName || !form.value.providerType || !form.value.baseUrl || !form.value.modelName) {
    ElMessage.warning('请填写配置名称、Provider 类型、Base URL 和模型名称')
    return false
  }
  if (requireApiKey && !form.value.apiKey) {
    ElMessage.warning('请填写 API Key')
    return false
  }
  if (form.value.modelParametersJson) {
    try {
      JSON.parse(form.value.modelParametersJson)
    } catch {
      ElMessage.warning('高级参数 JSON 格式不正确')
      return false
    }
  }
  return true
}

function buildTestRequest(): ProviderTestRequest | null {
  if (!validateForm(true)) return null
  return {
    providerType: form.value.providerType,
    providerLabel: form.value.providerLabel,
    baseUrl: form.value.baseUrl,
    apiKey: form.value.apiKey || '',
    modelName: form.value.modelName,
    temperature: form.value.temperature,
    maxTokens: form.value.maxTokens,
    timeoutSeconds: form.value.timeoutSeconds,
    modelParametersJson: form.value.modelParametersJson
  }
}

async function handleFetchModels(forceRefresh: boolean) {
  if (!form.value.providerType || !form.value.baseUrl) {
    ElMessage.warning('请先填写 Provider 类型和 Base URL')
    return
  }
  if (!editId.value && !form.value.apiKey) {
    ElMessage.warning('请先填写 API Key')
    return
  }
  if (editId.value && form.value.apiKey && !validateForm(true)) {
    return
  }
  fetchingModels.value = true
  try {
    const result = editId.value && !form.value.apiKey
      ? await fetchModelsByConfig(editId.value, forceRefresh)
      : await fetchModels({
          providerType: form.value.providerType,
          baseUrl: form.value.baseUrl,
          apiKey: form.value.apiKey || '',
          forceRefresh
        })
    fetchedModels.value = result.models || []
    ElMessage.success(`获取到 ${fetchedModels.value.length} 个模型${result.fromCache ? '（缓存）' : ''}`)
  } finally {
    fetchingModels.value = false
  }
}

async function handleFetchModelsSaved(row: ApiConfig) {
  fetchingModels.value = true
  try {
    const result = await fetchModelsByConfig(row.id, true)
    ElMessage.success(`获取到 ${result.models.length} 个模型`)
  } finally {
    fetchingModels.value = false
  }
}

async function handleTestConnection() {
  testingConnection.value = true
  try {
    const request = editId.value && !form.value.apiKey ? null : buildTestRequest()
    if (!editId.value && !request) return
    const result = editId.value && !form.value.apiKey
      ? await testConnectionByConfig(editId.value)
      : await testConnection(request!)
    ElMessage[result.success ? 'success' : 'error'](result.message)
  } finally {
    testingConnection.value = false
  }
}

async function handleTestStructured() {
  testingStructured.value = true
  try {
    const request = editId.value && !form.value.apiKey ? null : buildTestRequest()
    if (!editId.value && !request) return
    const result = editId.value && !form.value.apiKey
      ? await testStructuredOutputByConfig(editId.value)
      : await testStructuredOutput(request!)
    ElMessage[result.success ? 'success' : 'error'](result.message)
  } finally {
    testingStructured.value = false
  }
}

async function handleTestSaved(row: ApiConfig) {
  const result = await testConnectionByConfig(row.id)
  ElMessage[result.success ? 'success' : 'error'](result.message)
  configStore.loadConfigs()
}

async function handleRevealKey() {
  if (!editId.value) return
  revealingKey.value = true
  try {
    const result = await revealApiKey(editId.value)
    await ElMessageBox.alert(result.apiKey, '完整 API Key', {
      confirmButtonText: '关闭'
    })
  } finally {
    revealingKey.value = false
  }
}

async function handleSubmit() {
  if (!validateForm(!editId.value)) return
  submitting.value = true
  try {
    const payload: ApiConfigRequest = { ...form.value }
    if (editId.value && !payload.apiKey) {
      delete payload.apiKey
    }
    if (editId.value) {
      await updateConfig(editId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await createConfig(payload)
      ElMessage.success('创建成功')
    }
    showDialog.value = false
    configStore.loadConfigs()
  } finally {
    submitting.value = false
  }
}

async function handleSetDefault(row: ApiConfig) {
  await setDefaultConfig(row.id)
  ElMessage.success('设置成功')
  configStore.loadConfigs()
}

async function handleDelete(row: ApiConfig) {
  await ElMessageBox.confirm('确定删除此配置吗？', '提示', {
    type: 'warning'
  })
  await deleteConfig(row.id)
  ElMessage.success('删除成功')
  configStore.loadConfigs()
}
</script>

<style scoped>
.config-view {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.full-width {
  width: 100%;
}

.hint,
.muted {
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}

.test-message {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.model-select {
  flex: 1;
}

.mt-8 {
  margin-top: 8px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
