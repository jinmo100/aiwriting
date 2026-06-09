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
        <div><span class="muted">版本：</span>v{{ detail.essay.versionNo || 1 }}</div>
        <div><span class="muted">模型：</span>{{ detail.aiModel || '等待调度' }}</div>
        <div><span class="muted">任务状态：</span>{{ statusLabel(detail.scoringStatus) }}</div>
        <div><span class="muted">尝试次数：</span>第 {{ detail.attemptCount || 1 }} 次</div>
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
        :title="detail.retryable ? 'AI评分失败，可以直接重试' : 'AI评分失败，需要调整后重新提交'"
        :description="detail.errorMessage || '评分任务执行失败。'"
        :closable="false"
        show-icon
      />
      <div class="overview-grid failed-info">
        <div><span class="muted">错误类型：</span>{{ detail.errorCode || 'UNKNOWN' }}</div>
        <div><span class="muted">尝试次数：</span>{{ detail.attemptCount || 1 }}/3</div>
        <div><span class="muted">是否可重试：</span>{{ detail.retryable ? '可以重试' : '不可直接重试' }}</div>
        <div><span class="muted">模型：</span>{{ detail.aiModel || '未知' }}</div>
      </div>
      <div class="actions">
        <el-button
          v-if="detail.retryable"
          type="primary"
          :loading="retrying"
          @click="handleRetry"
        >
          重试评分
        </el-button>
        <el-button type="primary" plain @click="router.push('/submit')">修改后重新提交</el-button>
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
          <div><span class="muted">版本：</span>v{{ detail.essay.versionNo || 1 }}</div>
          <div><span class="muted">模型：</span>{{ detail.aiModel || '未知' }}</div>
          <div><span class="muted">评分耗时：</span>{{ detail.processingTime || 0 }}ms</div>
          <div><span class="muted">Rubric：</span>{{ scoring.rubric.name }} / {{ scoring.rubric.version }}</div>
          <div><span class="muted">尝试次数：</span>第 {{ detail.attemptCount || 1 }} 次</div>
        </div>
      </el-card>

      <el-card v-if="aiUsage" class="feedback-card">
        <template #header>
          <div class="card-header">
            <span>AI 调用与 Token 消耗</span>
            <el-tag type="info">{{ usageSourceLabel(aiUsage.usageSource) }}</el-tag>
          </div>
        </template>
        <div class="overview-grid">
          <div><span class="muted">Provider：</span>{{ aiUsage.provider || '未知' }}</div>
          <div><span class="muted">Endpoint：</span>{{ aiUsage.endpointType || '未知' }}</div>
          <div><span class="muted">Model：</span>{{ aiUsage.model || detail.aiModel || '未知' }}</div>
          <div><span class="muted">调用次数：</span>{{ aiUsage.invocationCount }}</div>
          <div><span class="muted">输入 Token：</span>{{ formatToken(aiUsage.inputTokens, aiUsage.usageSource) }}</div>
          <div><span class="muted">输出 Token：</span>{{ formatToken(aiUsage.outputTokens, aiUsage.usageSource) }}</div>
          <div><span class="muted">总 Token：</span>{{ formatToken(aiUsage.totalTokens, aiUsage.usageSource) }}</div>
          <div><span class="muted">AI Thinking 用时：</span>{{ aiUsage.latencyMs || detail.processingTime || 0 }}ms</div>
          <div v-if="aiUsage.estimatedCost !== undefined && aiUsage.estimatedCost !== null">
            <span class="muted">预计费用：</span>{{ formatCost(aiUsage.estimatedCost, aiUsage.currency, aiUsage.usageSource) }}
          </div>
        </div>
        <el-collapse v-if="aiUsage.invocations?.length" class="usage-details">
          <el-collapse-item title="查看调用明细" name="details">
            <el-table :data="aiUsage.invocations" size="small">
              <el-table-column prop="purpose" label="用途" min-width="120" />
              <el-table-column prop="status" label="状态" width="90" />
              <el-table-column prop="model" label="模型" min-width="160" />
              <el-table-column label="Token" min-width="180">
                <template #default="{ row }">
                  {{ formatToken(row.totalTokens, row.usageSource) }}
                  <span class="muted">（{{ row.inputTokens || 0 }}/{{ row.outputTokens || 0 }}）</span>
                </template>
              </el-table-column>
              <el-table-column label="耗时" width="100">
                <template #default="{ row }">{{ row.latencyMs || 0 }}ms</template>
              </el-table-column>
              <el-table-column label="费用" min-width="120">
                <template #default="{ row }">
                  <span v-if="row.estimatedCost !== undefined && row.estimatedCost !== null">
                    {{ formatCost(row.estimatedCost, row.currency, row.usageSource) }}
                  </span>
                  <span v-else class="muted">未配置单价</span>
                </template>
              </el-table-column>
            </el-table>
          </el-collapse-item>
        </el-collapse>
      </el-card>

      <el-card v-if="hasTaskPrompt" class="feedback-card">
        <template #header>题目/任务要求</template>
        <p class="feedback-text">{{ detail.essay.taskPrompt }}</p>
      </el-card>

      <el-card class="feedback-card">
        <template #header>
          <div class="card-header">
            <span>知识点增强反馈</span>
            <el-button
              v-if="!ragContent && !isRagActive"
              type="primary"
              size="small"
              :loading="ragGenerating"
              @click="handleGenerateRag"
            >
              生成反馈
            </el-button>
          </div>
        </template>

        <div v-loading="ragLoading">
          <el-alert
            v-if="isRagActive"
            type="info"
            title="知识点分析中，请稍候刷新"
            :closable="false"
            show-icon
          />
          <template v-else-if="ragContent">
            <p class="feedback-text">{{ ragContent.overall }}</p>
            <div class="rag-item-list">
              <div v-for="(item, index) in ragContent.items" :key="index" class="rag-item">
                <h4>{{ item.title }}</h4>
                <p><span class="muted">问题：</span>{{ item.problem }}</p>
                <p><span class="muted">影响：</span>{{ item.whyItMatters }}</p>
                <p><span class="muted">改法：</span>{{ item.howToImprove }}</p>
                <p v-if="item.example?.before || item.example?.after">
                  <span class="muted">示例：</span>
                  {{ item.example?.before }} → {{ item.example?.after }}
                </p>
                <div class="citation-list">
                  <el-tag
                    v-for="citationId in item.citationIds"
                    :key="citationId"
                    size="small"
                    type="success"
                  >
                    引用 {{ citationId }}
                  </el-tag>
                </div>
              </div>
            </div>
            <div v-if="ragFeedback?.citations?.length" class="citation-panel">
              <div class="block-title">引用来源</div>
              <div v-for="citation in ragFeedback.citations" :key="citation.rankNo" class="citation-card">
                <strong>#{{ citation.rankNo }} {{ citation.sourceTitle }}</strong>
                <p>{{ citation.snippet }}</p>
                <small class="muted">{{ citation.sourceType }} · {{ citation.reason }}</small>
              </div>
            </div>
            <div class="block-title">下一步练习</div>
            <ul class="plain-list">
              <li v-for="(practice, index) in ragContent.nextPractice" :key="index">{{ practice }}</li>
            </ul>
          </template>
          <el-alert
            v-else-if="ragFeedback?.status === 'FAILED'"
            type="warning"
            title="知识点增强反馈生成失败，但不影响本次作文评分结果"
            :description="ragFeedback.errorMessage || ragFeedback.message"
            show-icon
            :closable="false"
          >
            <template #default>
              <el-button size="small" type="primary" :loading="ragGenerating" @click="handleRetryRag">重试</el-button>
            </template>
          </el-alert>
          <el-alert
            v-else-if="ragFeedback?.status === 'SKIPPED'"
            type="info"
            :title="ragFeedback.message || '暂时无法生成知识点增强反馈'"
            :closable="false"
            show-icon
          >
            <template #default>
              <el-button size="small" @click="router.push('/embedding-config')">去配置 Embedding</el-button>
            </template>
          </el-alert>
          <el-empty v-else description="尚未生成知识点增强反馈">
            <el-button type="primary" :loading="ragGenerating" @click="handleGenerateRag">生成知识点增强反馈</el-button>
            <el-button @click="router.push('/embedding-config')">Embedding 配置</el-button>
          </el-empty>
        </div>
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
          <div :id="`annotation-${index}`" v-for="(item, index) in scoring.annotations" :key="index" class="annotation-card">
            <div class="annotation-head">
              <el-tag size="small" type="danger">{{ item.type }}</el-tag>
              <el-tag size="small" type="warning">{{ item.severity }}</el-tag>
            </div>
            <p><span class="muted">原文片段：</span>{{ item.original || item.context }}</p>
            <p v-if="item.quote"><span class="muted">高亮定位：</span>{{ item.quote }}</p>
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

      <el-card v-if="scoring.referenceEssay?.content" class="feedback-card">
        <template #header>{{ scoring.referenceEssay.title || '同水平提升版范文' }}</template>
        <el-alert
          type="success"
          title="这不是满分范文，而是在保留原意和当前能力层级基础上的改写示例"
          :closable="false"
          show-icon
        />
        <p class="essay-content reference-essay">{{ scoring.referenceEssay.content }}</p>
        <ul v-if="scoring.referenceEssay.notes?.length" class="plain-list">
          <li v-for="(note, index) in scoring.referenceEssay.notes" :key="index">{{ note }}</li>
        </ul>
      </el-card>

      <el-card class="feedback-card">
        <template #header>原文与批注高亮</template>
        <p class="essay-content">
          <template v-for="(segment, index) in highlightedEssaySegments" :key="index">
            <mark
              v-if="segment.annotationIndex !== undefined"
              class="essay-highlight"
              :class="highlightClass(segment.severity)"
              @click="scrollToAnnotation(segment.annotationIndex)"
            >{{ segment.text }}</mark>
            <span v-else>{{ segment.text }}</span>
          </template>
        </p>
        <p v-if="!hasHighlightedAnnotations" class="muted">暂无可定位批注，已在上方列出未定位建议。</p>
      </el-card>

      <div class="actions">
        <el-button type="primary" @click="router.push('/submit')">继续评分</el-button>
        <el-button type="success" @click="submitRevision">提交修改版</el-button>
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
import { ElMessage } from 'element-plus'
import { CircleCloseFilled, SuccessFilled, WarningFilled } from '@element-plus/icons-vue'
import { getEssayDetail, retryEssay } from '@/api/essay'
import { getRagFeedback, generateRagFeedback, retryRagFeedback } from '@/api/rag'
import type { EssayScoreResponse, RagFeedback, RagFeedbackContent } from '@/types'
import { buildHighlightedSegments } from '@/utils/annotationHighlight'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const retrying = ref(false)
const detail = ref<EssayScoreResponse | null>(null)
const ragFeedback = ref<RagFeedback | null>(null)
const ragLoading = ref(false)
const ragGenerating = ref(false)
const thinkingStepIndex = ref(0)
let pollingTimer: number | undefined
let thinkingTimer: number | undefined
let ragPollingTimer: number | undefined
const pendingStorageKey = 'essay-evaluator:pendingSubmission'
const thinkingSteps = [
  '正在理解题目要求...',
  '正在检查作文结构...',
  '正在根据评分标准打分...',
  '正在整理修改建议...'
]

const scoring = computed(() => detail.value?.result || null)
const aiUsage = computed(() => detail.value?.aiUsage || null)
const ragContent = computed<RagFeedbackContent | null>(() => {
  if (!ragFeedback.value?.feedbackJson) return null
  try {
    return JSON.parse(ragFeedback.value.feedbackJson) as RagFeedbackContent
  } catch {
    return null
  }
})
const isRagActive = computed(() => ['PENDING', 'RUNNING'].includes(ragFeedback.value?.status || ''))
const highlightedEssaySegments = computed(() => buildHighlightedSegments(
  detail.value?.essay.content || '',
  scoring.value?.annotations || []
))
const hasHighlightedAnnotations = computed(() => highlightedEssaySegments.value.some((segment) => segment.annotationIndex !== undefined))
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
  stopRagPolling()
})

async function loadDetail(id: number, showLoading = false) {
  if (showLoading) {
    loading.value = true
  }
  try {
    detail.value = await getEssayDetail(id)
    if (detail.value.scoringStatus === 'COMPLETED') {
      loadRagFeedback(id)
    }
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

async function loadRagFeedback(id: number, showLoading = false) {
  if (showLoading) {
    ragLoading.value = true
  }
  try {
    ragFeedback.value = await getRagFeedback(id)
    if (isRagActive.value) {
      startRagPolling()
    } else {
      stopRagPolling()
    }
  } catch (error) {
    console.error('加载 RAG Feedback 失败:', error)
  } finally {
    ragLoading.value = false
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

function startRagPolling() {
  if (ragPollingTimer) return
  ragPollingTimer = window.setInterval(() => {
    const id = Number(route.params.id)
    if (id) {
      loadRagFeedback(id)
    }
  }, 3000)
}

function stopRagPolling() {
  if (ragPollingTimer) {
    window.clearInterval(ragPollingTimer)
    ragPollingTimer = undefined
  }
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

function submitRevision() {
  if (!detail.value?.essay.id) return
  router.push({ path: '/submit', query: { parentEssayId: String(detail.value.essay.id) } })
}

async function handleRetry() {
  const id = Number(route.params.id)
  if (!id || retrying.value) return
  retrying.value = true
  try {
    detail.value = await retryEssay(id)
    ElMessage.success('已重新进入 AI Thinking，请稍等')
    if (isPending.value) {
      startPolling()
    }
  } catch (error) {
    console.error('重试失败:', error)
  } finally {
    retrying.value = false
  }
}

async function handleGenerateRag() {
  const id = Number(route.params.id)
  if (!id || ragGenerating.value) return
  ragGenerating.value = true
  try {
    ragFeedback.value = await generateRagFeedback(id)
    ElMessage.success(
      ragFeedback.value.status === 'SKIPPED'
        ? (ragFeedback.value.message || '请先完成 Embedding 配置或知识索引')
        : '知识点增强反馈任务已提交'
    )
    if (isRagActive.value) {
      startRagPolling()
    }
  } catch (error) {
    console.error('生成 RAG Feedback 失败:', error)
  } finally {
    ragGenerating.value = false
  }
}

async function handleRetryRag() {
  const id = Number(route.params.id)
  if (!id || ragGenerating.value) return
  ragGenerating.value = true
  try {
    ragFeedback.value = await retryRagFeedback(id)
    ElMessage.success('已重试知识点增强反馈')
    if (isRagActive.value) {
      startRagPolling()
    }
  } catch (error) {
    console.error('重试 RAG Feedback 失败:', error)
  } finally {
    ragGenerating.value = false
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

function usageSourceLabel(source?: string) {
  if (source === 'PROVIDER') return 'Provider 返回'
  if (source === 'LOCAL_ESTIMATE') return '本地估算'
  if (source === 'MIXED') return '混合统计'
  return '未返回用量'
}

function formatToken(value?: number, source?: string) {
  if (value === undefined || value === null) return '未返回'
  const prefix = source === 'LOCAL_ESTIMATE' || source === 'MIXED' ? '约 ' : ''
  return `${prefix}${value.toLocaleString()}`
}

function formatCost(value?: number, currency?: string, source?: string) {
  if (value === undefined || value === null) return '未配置单价'
  const prefix = source === 'LOCAL_ESTIMATE' || source === 'MIXED' ? '约 ' : ''
  return `${prefix}${currency || ''} ${Number(value).toFixed(6)}`.trim()
}

function highlightClass(severity?: string) {
  if (severity === 'HIGH' || severity === '严重') return 'highlight-high'
  if (severity === 'LOW' || severity === '轻微') return 'highlight-low'
  return 'highlight-medium'
}

function scrollToAnnotation(index?: number) {
  if (index === undefined) return
  document.getElementById(`annotation-${index}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
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

.failed-info {
  margin-top: 18px;
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

.reference-essay {
  margin-top: 12px;
}

.essay-highlight {
  padding: 1px 3px;
  border-radius: 4px;
  cursor: pointer;
}

.highlight-high {
  background: #fde2e2;
  color: #c45656;
}

.highlight-medium {
  background: #faecd8;
  color: #b88230;
}

.highlight-low {
  background: #e1f3d8;
  color: #529b2e;
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

.rag-item-list {
  display: grid;
  gap: 14px;
  margin-top: 14px;
}

.rag-item {
  padding: 14px 16px;
  border: 1px solid #d9ecff;
  border-radius: 10px;
  background: #f5faff;
}

.rag-item h4 {
  margin: 0 0 8px;
  color: #303133;
}

.rag-item p {
  margin: 6px 0;
  line-height: 1.7;
}

.citation-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.citation-panel {
  margin-top: 18px;
}

.citation-card {
  padding: 12px 14px;
  margin-top: 10px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fafafa;
}

.citation-card p {
  margin: 6px 0;
  line-height: 1.7;
}

.usage-details {
  margin-top: 14px;
}

.full-height {
  height: calc(100% - 20px);
}

.actions {
  text-align: center;
  margin-top: 30px;
}
</style>
