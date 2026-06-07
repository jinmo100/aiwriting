<template>
  <div class="result-view">
    <el-card v-if="loading" v-loading="loading" element-loading-text="加载中...">
      <div style="height: 400px"></div>
    </el-card>

    <el-card v-else-if="detail && isPending" class="score-card pending-card">
      <template #header>
        <div class="card-header">
          <span>AI Thinking</span>
          <el-tag type="warning">{{ statusLabel(detail.scoringStatus) }}</el-tag>
        </div>
      </template>
      <el-alert
        type="info"
        title="正在根据评分标准分析你的作文，请稍等"
        :closable="false"
        show-icon
      />
      <div class="thinking-step">{{ thinkingSteps[thinkingStepIndex] }}</div>
      <el-progress :percentage="thinkingProgress" :indeterminate="true" :duration="2" />
      <div class="overview-grid pending-info">
        <div><span class="muted">作文类型：</span>{{ detail.essay.essayTypeDisplayName }}</div>
        <div><span class="muted">英文词数：</span>{{ detail.essay.wordCount }}</div>
        <div><span class="muted">模型：</span>{{ detail.aiModel || '等待调度' }}</div>
        <div><span class="muted">任务状态：</span>{{ statusLabel(detail.scoringStatus) }}</div>
      </div>
      <div class="actions">
        <el-button @click="reloadNow">立即刷新</el-button>
        <el-button @click="router.push('/history')">查看历史</el-button>
      </div>
    </el-card>

    <el-card v-else-if="detail && isFailed" class="score-card">
      <template #header>
        <div class="card-header">
          <span>评分失败</span>
          <el-tag type="danger">FAILED</el-tag>
        </div>
      </template>
      <el-alert
        type="error"
        title="AI评分失败，请稍后重新提交"
        :description="detail.errorMessage || '评分任务执行失败。'"
        :closable="false"
        show-icon
      />
      <div class="actions">
        <el-button type="primary" @click="router.push('/submit')">重新提交</el-button>
        <el-button @click="router.push('/history')">查看历史</el-button>
      </div>
    </el-card>

    <template v-else-if="detail && scoring">
      <el-card class="score-card">
        <template #header>
          <div class="card-header">
            <span>评分结果</span>
            <div class="header-tags">
              <el-tag :type="gradeTagType(scoring.gradeLabel)">{{ scoring.gradeLabel }}</el-tag>
              <el-tag type="info">{{ scoring.rubric.version }}</el-tag>
            </div>
          </div>
        </template>

        <el-row :gutter="20">
          <el-col :xs="24" :sm="12" :md="6">
            <div class="metric-card">
              <div class="metric-title">原生分</div>
              <div class="metric-value">{{ scoring.nativeScore.display }}</div>
            </div>
          </el-col>
          <el-col :xs="24" :sm="12" :md="6">
            <div class="metric-card">
              <div class="metric-title">换算分</div>
              <div class="metric-value">{{ scoring.normalizedScore.display }}</div>
            </div>
          </el-col>
          <el-col :xs="24" :sm="12" :md="6">
            <div class="metric-card">
              <div class="metric-title">置信度</div>
              <div class="metric-value">{{ confidenceDisplay }}</div>
            </div>
          </el-col>
          <el-col :xs="24" :sm="12" :md="6">
            <div class="metric-card">
              <div class="metric-title">英文词数</div>
              <div class="metric-value">{{ detail.essay.wordCount }}</div>
            </div>
          </el-col>
        </el-row>

        <el-divider />

        <div class="overview-grid">
          <div><span class="muted">作文类型：</span>{{ detail.essay.essayTypeDisplayName }}</div>
          <div><span class="muted">模型：</span>{{ detail.aiModel || '未知' }}</div>
          <div><span class="muted">评分耗时：</span>{{ detail.processingTime || 0 }}ms</div>
          <div><span class="muted">Rubric：</span>{{ scoring.rubric.name }} / {{ scoring.rubric.version }}</div>
        </div>
      </el-card>

      <el-card v-if="hasTaskPrompt" class="feedback-card">
        <template #header>题目/任务要求</template>
        <p class="feedback-text">{{ detail.essay.taskPrompt }}</p>
      </el-card>

      <el-card class="feedback-card">
        <template #header>
          <span>评分维度</span>
        </template>
        <div class="dimension-list">
          <div v-for="dimension in scoring.dimensions" :key="dimension.key" class="dimension-card">
            <div class="dimension-head">
              <div>
                <strong>{{ dimension.label }}</strong>
                <el-tag size="small" class="level-tag">{{ dimension.level }}</el-tag>
              </div>
              <span class="dimension-score">{{ formatScore(dimension.score) }}/{{ formatScore(dimension.maxScore) }}</span>
            </div>
            <el-progress
              :percentage="dimensionPercentage(dimension.score, dimension.maxScore)"
              :stroke-width="10"
              :show-text="false"
            />
            <p class="dimension-reason">{{ dimension.reason }}</p>
            <div v-if="dimension.evidence.length" class="evidence-block">
              <div class="block-title">证据</div>
              <ul>
                <li v-for="(item, index) in dimension.evidence" :key="index">{{ item }}</li>
              </ul>
            </div>
            <div class="improvement-block">
              <div class="block-title">提升建议</div>
              <p>{{ dimension.improvement }}</p>
            </div>
          </div>
        </div>
      </el-card>

      <el-row :gutter="20">
        <el-col :xs="24" :md="12">
          <el-card class="feedback-card full-height">
            <template #header>
              <el-icon color="#67C23A"><SuccessFilled /></el-icon>
              <span>主要优点</span>
            </template>
            <el-timeline>
              <el-timeline-item
                v-for="(item, index) in scoring.summary.strengths"
                :key="index"
                color="#67C23A"
              >
                {{ item }}
              </el-timeline-item>
            </el-timeline>
          </el-card>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-card class="feedback-card full-height">
            <template #header>
              <el-icon color="#E6A23C"><WarningFilled /></el-icon>
              <span>优先改进建议</span>
            </template>
            <el-timeline>
              <el-timeline-item
                v-for="(item, index) in scoring.summary.priorityImprovements"
                :key="index"
                color="#E6A23C"
              >
                {{ item }}
              </el-timeline-item>
            </el-timeline>
          </el-card>
        </el-col>
      </el-row>

      <el-card v-if="scoring.annotations.length > 0" class="feedback-card">
        <template #header>
          <el-icon color="#F56C6C"><CircleCloseFilled /></el-icon>
          <span>逐句/片段问题</span>
        </template>
        <div class="annotation-list">
          <div v-for="(item, index) in scoring.annotations" :key="index" class="annotation-card">
            <div class="annotation-head">
              <el-tag size="small" type="danger">{{ item.type }}</el-tag>
              <el-tag size="small" type="warning">{{ item.severity }}</el-tag>
            </div>
            <p><span class="muted">原文片段：</span>{{ item.original || item.context }}</p>
            <p><span class="muted">问题：</span>{{ item.message }}</p>
            <p><span class="muted">建议：</span>{{ item.suggestion }}</p>
            <p v-if="item.explanation"><span class="muted">说明：</span>{{ item.explanation }}</p>
          </div>
        </div>
      </el-card>

      <el-card v-if="scoring.inputAnalysis || scoring.safetyNotice || scoring.confidence.warnings.length" class="feedback-card">
        <template #header>输入质量 / 安全提示</template>
        <el-alert
          v-if="scoring.inputAnalysis?.status === 'WARN'"
          type="warning"
          title="输入存在可改进点，但已继续评分"
          :closable="false"
          show-icon
        />
        <ul class="plain-list">
          <li v-for="(warning, index) in scoring.inputAnalysis?.warnings || []" :key="`input-${index}`">{{ warning }}</li>
          <li v-for="(warning, index) in scoring.confidence.warnings" :key="`confidence-${index}`">{{ warning }}</li>
          <li v-if="scoring.safetyNotice">{{ scoring.safetyNotice }}</li>
        </ul>
      </el-card>

      <el-card class="feedback-card">
        <template #header>总体评价</template>
        <p class="feedback-text">{{ scoring.summary.overallFeedback }}</p>
      </el-card>

      <el-card class="feedback-card">
        <template #header>原文</template>
        <p class="essay-content">{{ detail.essay.content }}</p>
      </el-card>

      <div class="actions">
        <el-button type="primary" @click="router.push('/submit')">继续评分</el-button>
        <el-button @click="router.push('/history')">查看历史</el-button>
      </div>
    </template>

    <el-empty v-else description="未找到评分结果">
      <el-button type="primary" @click="router.push('/submit')">返回提交页</el-button>
    </el-empty>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CircleCloseFilled, SuccessFilled, WarningFilled } from '@element-plus/icons-vue'
import { getEssayDetail } from '@/api/essay'
import type { EssayScoreResponse } from '@/types'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const detail = ref<EssayScoreResponse | null>(null)
const thinkingStepIndex = ref(0)
let pollingTimer: number | undefined
let thinkingTimer: number | undefined
const pendingStorageKey = 'essay-evaluator:pendingSubmission'
const thinkingSteps = [
  '正在理解题目要求...',
  '正在检查作文结构...',
  '正在根据评分标准打分...',
  '正在整理修改建议...'
]

const scoring = computed(() => detail.value?.result || null)
const isPending = computed(() => Boolean(detail.value && ['PENDING', 'SCORING'].includes(detail.value.scoringStatus) && !scoring.value))
const isFailed = computed(() => detail.value?.scoringStatus === 'FAILED')
const thinkingProgress = computed(() => 25 + thinkingStepIndex.value * 20)
const hasTaskPrompt = computed(() => Boolean(detail.value?.essay.taskPrompt?.trim()))
const confidenceDisplay = computed(() => {
  const confidence = scoring.value?.confidence
  if (!confidence) return '未知'
  return `${confidence.level} ${(confidence.score * 100).toFixed(0)}%`
})

onMounted(() => {
  const id = route.params.id as string
  if (!id) {
    router.push('/submit')
    return
  }

  loadDetail(Number(id), true)
})

onUnmounted(() => {
  stopPolling()
})

async function loadDetail(id: number, showLoading = false) {
  if (showLoading) {
    loading.value = true
  }
  try {
    detail.value = await getEssayDetail(id)
    if (isPending.value) {
      startPolling()
    } else {
      stopPolling()
      if (detail.value?.scoringStatus === 'COMPLETED' || detail.value?.scoringStatus === 'FAILED') {
        sessionStorage.removeItem(pendingStorageKey)
      }
    }
  } catch (error) {
    console.error('加载失败:', error)
  } finally {
    if (showLoading) {
      loading.value = false
    }
  }
}

function startPolling() {
  startThinkingTimer()
  if (pollingTimer) return
  pollingTimer = window.setInterval(() => {
    const id = Number(route.params.id)
    if (id) {
      loadDetail(id, false)
    }
  }, 3000)
}

function stopPolling() {
  if (pollingTimer) {
    window.clearInterval(pollingTimer)
    pollingTimer = undefined
  }
  stopThinkingTimer()
}

function startThinkingTimer() {
  if (thinkingTimer) return
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

function reloadNow() {
  const id = Number(route.params.id)
  if (id) {
    loadDetail(id, false)
  }
}

function dimensionPercentage(score: number, maxScore: number) {
  if (!maxScore) return 0
  return Math.round((score / maxScore) * 100)
}

function formatScore(value: number) {
  return Number.isInteger(value) ? value.toFixed(0) : value.toFixed(1)
}

function gradeTagType(label: string) {
  if (['优秀', '高分段'].includes(label)) return 'success'
  if (['良好'].includes(label)) return 'primary'
  if (['中等', '合格'].includes(label)) return 'warning'
  if (['及格', '基础'].includes(label)) return 'info'
  return 'danger'
}

function statusLabel(status: string) {
  if (status === 'COMPLETED') return '已完成'
  if (status === 'SCORING') return '评分中'
  if (status === 'PENDING') return '等待评分'
  if (status === 'FAILED') return '失败'
  return status
}
</script>

<style scoped>
.result-view {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.score-card,
.feedback-card {
  margin-bottom: 20px;
}

.pending-card {
  min-height: 360px;
}

.pending-info {
  margin-top: 24px;
}

.thinking-step {
  margin: 18px 0 12px;
  color: #409eff;
  font-weight: 700;
}

.card-header,
.header-tags,
.dimension-head,
.annotation-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 10px 20px;
  line-height: 1.8;
}

.metric-card {
  padding: 14px 16px;
  border-radius: 10px;
  background: #f7f9fc;
  text-align: center;
}

.metric-title {
  color: #909399;
  font-size: 13px;
  margin-bottom: 8px;
}

.metric-value {
  color: #303133;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
}

.muted {
  color: #909399;
}

.dimension-list,
.annotation-list {
  display: grid;
  gap: 14px;
}

.dimension-card,
.annotation-card {
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  background: #fafafa;
}

.dimension-score {
  font-size: 18px;
  font-weight: 700;
  color: #409eff;
}

.level-tag {
  margin-left: 8px;
}

.dimension-reason,
.feedback-text,
.essay-content {
  line-height: 1.8;
  text-align: justify;
  white-space: pre-wrap;
}

.evidence-block,
.improvement-block {
  margin-top: 10px;
}

.block-title {
  font-weight: 700;
  margin-bottom: 4px;
}

.plain-list {
  margin-left: 18px;
  line-height: 1.8;
}

.full-height {
  height: calc(100% - 20px);
}

.actions {
  text-align: center;
  margin-top: 30px;
}
</style>
