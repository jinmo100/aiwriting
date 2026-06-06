<template>
  <div class="result-view">
    <el-card v-if="loading" v-loading="loading" element-loading-text="加载中...">
      <div style="height: 400px"></div>
    </el-card>

    <template v-else-if="result">
      <!-- 分数展示 -->
      <el-card class="score-card">
        <template #header>
          <div class="card-header">
            <span>评分结果</span>
            <el-tag>总分: {{ result.score.overallScore }}</el-tag>
          </div>
        </template>

        <el-row :gutter="20">
          <el-col :span="6">
            <el-statistic title="内容完整性" :value="result.score.contentScore" suffix="/ 30" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="语言准确性" :value="result.score.languageScore" suffix="/ 30" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="文章结构" :value="result.score.structureScore" suffix="/ 20" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="连贯性" :value="result.score.coherenceScore" suffix="/ 20" />
          </el-col>
        </el-row>

        <el-divider />

        <div class="info-text">
          <p>处理耗时: {{ result.processingTime }}ms</p>
          <p>使用模型: {{ result.score.aiModel || '未知' }}</p>
        </div>
      </el-card>

      <!-- 优点 -->
      <el-card class="feedback-card">
        <template #header>
          <el-icon color="#67C23A"><SuccessFilled /></el-icon>
          <span>优点分析</span>
        </template>
        <el-timeline>
          <el-timeline-item
            v-for="(item, index) in result.score.strengths"
            :key="index"
            color="#67C23A"
          >
            {{ item }}
          </el-timeline-item>
        </el-timeline>
      </el-card>

      <!-- 建议 -->
      <el-card class="feedback-card">
        <template #header>
          <el-icon color="#E6A23C"><WarningFilled /></el-icon>
          <span>改进建议</span>
        </template>
        <el-timeline>
          <el-timeline-item
            v-for="(item, index) in result.score.suggestions"
            :key="index"
            color="#E6A23C"
          >
            {{ item }}
          </el-timeline-item>
        </el-timeline>
      </el-card>

      <!-- 错误 -->
      <el-card v-if="result.score.errors.length > 0" class="feedback-card">
        <template #header>
          <el-icon color="#F56C6C"><CircleCloseFilled /></el-icon>
          <span>错误标注</span>
        </template>
        <el-table :data="result.score.errors" border>
          <el-table-column prop="sentence" label="原句" width="300" />
          <el-table-column prop="type" label="类型" width="120" />
          <el-table-column prop="description" label="描述" />
          <el-table-column prop="correction" label="修改建议" />
        </el-table>
      </el-card>

      <!-- 详细反馈 -->
      <el-card class="feedback-card">
        <template #header>
          <span>详细评价</span>
        </template>
        <p class="feedback-text">{{ result.score.detailedFeedback }}</p>
      </el-card>

      <!-- 操作按钮 -->
      <div class="actions">
        <el-button type="primary" @click="router.push('/submit')">继续评分</el-button>
        <el-button @click="router.push('/history')">查看历史</el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { SuccessFilled, WarningFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import { getEssayDetail } from '@/api/essay'
import type { EssayScoreResponse } from '@/types'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const result = ref<EssayScoreResponse | null>(null)
const essay = ref<any>(null)

onMounted(async () => {
  const id = route.params.id as string
  if (!id) {
    router.push('/submit')
    return
  }

  loading.value = true
  try {
    const data = await getEssayDetail(Number(id))
    essay.value = data.essay
    // 这里需要构造EssayScoreResponse
    result.value = {
      essayId: data.essay.id,
      score: data.score,
      processingTime: data.score.processingTime
    }
  } catch (error) {
    console.error('加载失败:', error)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.result-view {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.score-card {
  margin-bottom: 20px;
}

.feedback-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.info-text {
  color: #909399;
  font-size: 14px;
}

.feedback-text {
  line-height: 1.8;
  text-align: justify;
}

.actions {
  text-align: center;
  margin-top: 30px;
}
</style>
