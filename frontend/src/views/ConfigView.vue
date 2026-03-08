<template>
  <div class="config-view">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>API配置管理</span>
          <el-button type="primary" @click="showDialog = true">
            <el-icon><Plus /></el-icon>
            新增配置
          </el-button>
        </div>
      </template>

      <el-table :data="configStore.configs" v-loading="configStore.loading">
        <el-table-column prop="configName" label="配置名称" />
        <el-table-column prop="provider" label="提供商" />
        <el-table-column prop="modelName" label="模型名称" />
        <el-table-column label="默认">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault" type="success">默认</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="warning" @click="handleSetDefault(row)" v-if="!row.isDefault">
              设为默认
            </el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 配置对话框 -->
    <el-dialog v-model="showDialog" :title="editId ? '编辑配置' : '新增配置'" width="600px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="配置名称" required>
          <el-input v-model="form.configName" placeholder="例如：OpenRouter Gemma" />
        </el-form-item>
        <el-form-item label="提供商" required>
          <el-select v-model="form.provider" placeholder="选择提供商">
            <el-option label="OpenRouter" value="openrouter" />
            <el-option label="OpenAI" value="openai" />
            <el-option label="Anthropic" value="anthropic" />
            <el-option label="DeepSeek" value="deepseek" />
          </el-select>
        </el-form-item>
        <el-form-item label="API Base URL" required>
          <el-input v-model="form.baseUrl" placeholder="例如：https://openrouter.ai/api/v1" />
        </el-form-item>
        <el-form-item label="API Key" required>
          <el-input v-model="form.apiKey" type="password" show-password placeholder="输入API Key" />
        </el-form-item>
        <el-form-item label="模型名称" required>
          <el-input v-model="form.modelName" placeholder="例如：google/gemma-2-9b-it:free" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useConfigStore } from '@/stores/useConfigStore'
import { createConfig, updateConfig, deleteConfig, setDefaultConfig } from '@/api/config'
import type { ApiConfig, ApiConfigRequest } from '@/types'

const configStore = useConfigStore()
const showDialog = ref(false)
const submitting = ref(false)
const editId = ref<number | null>(null)

const form = ref<ApiConfigRequest>({
  configName: '',
  provider: 'openrouter',
  baseUrl: '',
  apiKey: '',
  modelName: '',
  isDefault: false
})

onMounted(() => {
  configStore.loadConfigs()
})

function handleEdit(row: ApiConfig) {
  editId.value = row.id
  form.value = {
    configName: row.configName,
    provider: row.provider,
    baseUrl: row.baseUrl,
    apiKey: '', // API Key不回显
    modelName: row.modelName,
    isDefault: row.isDefault
  }
  showDialog.value = true
}

async function handleSubmit() {
  submitting.value = true
  try {
    if (editId.value) {
      await updateConfig(editId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await createConfig(form.value)
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
</style>
