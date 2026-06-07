<template>
  <div class="submit-view">
    <el-card>
      <template #header>
        <div class="header-title">
          <span>提交英语作文</span>
          <el-tag type="info">Rubric 动态评分</el-tag>
        </div>
      </template>

      <el-alert
        v-if="loading"
        class="thinking-alert"
        title="AI Thinking"
        type="info"
        :closable="false"
        show-icon
      >
        <template #default>
          <div>正在根据评分标准分析你的作文，请稍等</div>
          <div class="thinking-step">{{ thinkingSteps[thinkingStepIndex] }}</div>
        </template>
      </el-alert>

      <el-form :model="form" label-width="130px">
        <el-form-item label="作文类型" required>
          <el-select v-model="form.essayType" placeholder="请选择作文类型" class="full-width">
            <el-option
              v-for="option in essayTypeOptions"
              :key="option.code"
              :label="option.label"
              :value="option.code"
              :disabled="!option.enabled"
            >
              <div class="option-row">
                <span>{{ option.label }}</span>
                <el-tag v-if="!option.enabled" size="small" type="info">暂缓开放</el-tag>
                <el-tag v-else size="small" type="success">{{ option.wordRange }}</el-tag>
              </div>
            </el-option>
          </el-select>
          <div class="type-help">
            <div>{{ selectedType.description }}</div>
            <div>{{ selectedType.wordRange }}，字符上限 {{ selectedType.charLimit }}</div>
          </div>
        </el-form-item>

        <el-form-item :label="selectedType.taskPromptRequired ? '题目/任务要求' : '题目/任务要求（可选）'" :required="selectedType.taskPromptRequired">
          <el-input
            v-model="form.taskPrompt"
            type="textarea"
            :rows="4"
            :placeholder="taskPromptPlaceholder"
            maxlength="4000"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="作文正文" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="15"
            placeholder="请输入主要由英文构成的作文正文。请不要在正文中写入让 AI 忽略规则、直接给满分等指令。"
            :maxlength="selectedType.charLimit"
            show-word-limit
          />
          <div class="content-help">
            当前英文词数约 {{ englishWordCount }}；{{ selectedType.wordRange }}。
          </div>
        </el-form-item>

        <el-form-item label="API配置">
          <el-select v-model="form.configId" placeholder="可选；不选则使用默认配置" clearable class="full-width">
            <el-option
              v-for="config in configStore.configs"
              :key="config.id"
              :label="`${config.configName} (${config.modelName})${config.isDefault ? ' · 默认' : ''}`"
              :value="config.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSubmit" :loading="loading" size="large">
            {{ loading ? 'AI Thinking...' : '提交评分' }}
          </el-button>
          <el-button @click="handleClear" :disabled="loading" size="large">清空</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useConfigStore } from '@/stores/useConfigStore'
import { useEssayStore } from '@/stores/useEssayStore'
import { submitEssay } from '@/api/essay'
import type { EssaySubmitRequest, EssayTypeCode } from '@/types'
import { ESSAY_TYPE_OPTIONS } from '@/types'

const router = useRouter()
const configStore = useConfigStore()
const essayStore = useEssayStore()
const loading = ref(false)
const thinkingStepIndex = ref(0)
let thinkingTimer: number | undefined

const thinkingSteps = [
  '正在理解题目要求...',
  '正在检查作文结构...',
  '正在根据评分标准打分...',
  '正在整理修改建议...'
]
const pendingStorageKey = 'essay-evaluator:pendingSubmission'

const form = ref<EssaySubmitRequest>({
  content: '',
  essayType: 'GENERAL',
  taskPrompt: '',
  configId: undefined
})

const essayTypeOptions = ESSAY_TYPE_OPTIONS

const selectedType = computed(() => {
  return essayTypeOptions.find(item => item.code === form.value.essayType) || essayTypeOptions[0]
})

const taskPromptPlaceholder = computed(() => {
  if (selectedType.value.taskPromptPlaceholder) return selectedType.value.taskPromptPlaceholder
  if (selectedType.value.taskPromptRequired) return '请粘贴中文或英文题目/写作任务要求，评分会按该任务判断是否切题。'
  return '可填写题目或写作背景；不填写则按通用英语作文评分。'
})

const englishWordCount = computed(() => {
  const matches = form.value.content.match(/[A-Za-z]+(?:[-'][A-Za-z]+)?/g)
  return matches ? matches.length : 0
})

onMounted(() => {
  configStore.loadConfigs()
})

onUnmounted(() => {
  stopThinkingTimer()
})

async function handleSubmit() {
  if (loading.value) return

  if (!selectedType.value.enabled) {
    ElMessage.warning('该作文类型暂缓开放')
    return
  }
  if (!form.value.content.trim()) {
    ElMessage.warning('请填写作文正文')
    return
  }
  if (selectedType.value.taskPromptRequired && !form.value.taskPrompt?.trim()) {
    ElMessage.warning('该作文类型需要填写题目/任务要求')
    return
  }
  if (!configStore.defaultConfig && !form.value.configId) {
    ElMessage.warning('请先配置API或选择一个配置')
    return
  }

  loading.value = true
  startThinkingTimer()
  let submitted = false
  try {
    const payload: EssaySubmitRequest = {
      content: form.value.content,
      essayType: form.value.essayType as EssayTypeCode,
      taskPrompt: form.value.taskPrompt?.trim() || undefined,
      configId: form.value.configId,
      idempotencyKey: createIdempotencyKey()
    }
    savePending(payload.idempotencyKey)
    const result = await submitEssay(payload)
    submitted = true
    ElMessage.success(result.scoringStatus === 'COMPLETED' ? '评分成功！' : '评分任务已提交，AI Thinking...')
    essayStore.setCurrentResult(result)
    savePending(payload.idempotencyKey, result.essayId)
    router.push(`/result/${result.essayId}`)
  } catch (error) {
    console.error('评分失败:', error)
    if (!submitted) {
      sessionStorage.removeItem(pendingStorageKey)
    }
  } finally {
    loading.value = false
    stopThinkingTimer()
  }
}

function handleClear() {
  form.value = {
    content: '',
    essayType: 'GENERAL',
    taskPrompt: '',
    configId: undefined
  }
}

function startThinkingTimer() {
  thinkingStepIndex.value = 0
  stopThinkingTimer()
  thinkingTimer = window.setInterval(() => {
    thinkingStepIndex.value = (thinkingStepIndex.value + 1) % thinkingSteps.length
  }, 2200)
}

function stopThinkingTimer() {
  if (thinkingTimer) {
    window.clearInterval(thinkingTimer)
    thinkingTimer = undefined
  }
}

function createIdempotencyKey() {
  if (crypto?.randomUUID) {
    return crypto.randomUUID()
  }
  return `idem-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function savePending(idempotencyKey?: string, essayId?: number) {
  if (!idempotencyKey) return
  sessionStorage.setItem(pendingStorageKey, JSON.stringify({
    idempotencyKey,
    essayId,
    createdAt: Date.now(),
    essayType: form.value.essayType
  }))
}
</script>

<style scoped>
.submit-view {
  padding: 20px;
  max-width: 1000px;
  margin: 0 auto;
}

.header-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.full-width {
  width: 100%;
}

.option-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.type-help,
.content-help {
  color: #909399;
  font-size: 13px;
  line-height: 1.6;
  margin-top: 6px;
}

.thinking-alert {
  margin-bottom: 18px;
}

.thinking-step {
  margin-top: 6px;
  color: #409eff;
  font-weight: 600;
}
</style>
