<template>
  <div class="submit-view">
    <el-card>
      <template #header>
        <span>提交英语作文</span>
      </template>

      <el-form :model="form" label-width="120px">
        <el-form-item label="作文内容" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="15"
            placeholder="请输入英语作文内容（50-10000字）"
            maxlength="10000"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="作文类型">
          <el-select v-model="form.essayType" placeholder="选择作文类型（可选）">
            <el-option label="IELTS" value="IELTS" />
            <el-option label="TOEFL" value="TOEFL" />
            <el-option label="CET-4" value="CET4" />
            <el-option label="CET-6" value="CET6" />
          </el-select>
        </el-form-item>

        <el-form-item label="API配置">
          <el-select v-model="form.configId" placeholder="选择API配置（可选，默认使用默认配置）">
            <el-option
              v-for="config in configStore.configs"
              :key="config.id"
              :label="`${config.configName} (${config.modelName})`"
              :value="config.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSubmit" :loading="loading" size="large">
            提交评分
          </el-button>
          <el-button @click="handleClear" size="large">清空</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useConfigStore } from '@/stores/useConfigStore'
import { useEssayStore } from '@/stores/useEssayStore'
import { submitEssay } from '@/api/essay'
import type { EssaySubmitRequest } from '@/types'

const router = useRouter()
const configStore = useConfigStore()
const essayStore = useEssayStore()
const loading = ref(false)

const form = ref<EssaySubmitRequest>({
  content: '',
  essayType: undefined,
  configId: undefined
})

onMounted(() => {
  configStore.loadConfigs()
})

async function handleSubmit() {
  if (!form.value.content || form.value.content.length < 50) {
    ElMessage.warning('作文内容至少需要50个字符')
    return
  }

  if (!configStore.defaultConfig && !form.value.configId) {
    ElMessage.warning('请先配置API或选择一个配置')
    return
  }

  loading.value = true
  try {
    const result = await submitEssay(form.value)
    ElMessage.success('评分成功！')
    essayStore.setCurrentResult(result)
    router.push(`/result/${result.essayId}`)
  } catch (error) {
    console.error('评分失败:', error)
  } finally {
    loading.value = false
  }
}

function handleClear() {
  form.value = {
    content: '',
    essayType: undefined,
    configId: undefined
  }
}
</script>

<style scoped>
.submit-view {
  padding: 20px;
  max-width: 1000px;
  margin: 0 auto;
}
</style>
