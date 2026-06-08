<template>
  <div class="history-view">
    <el-card>
      <template #header>
        <div class="header-title">
          <span>历史记录</span>
          <div class="filters">
            <el-select v-model="typeFilter" placeholder="作文类型" clearable size="small" style="width: 180px">
              <el-option
                v-for="option in essayTypeOptions.filter(item => item.enabled)"
                :key="option.code"
                :label="option.label"
                :value="option.code"
              />
            </el-select>
            <el-select v-model="statusFilter" placeholder="状态" clearable size="small" style="width: 120px">
              <el-option label="评分中" value="SCORING" />
              <el-option label="已完成" value="COMPLETED" />
              <el-option label="失败" value="FAILED" />
              <el-option label="缺失" value="MISSING" />
            </el-select>
          </div>
        </div>
      </template>

      <el-table :data="filteredHistory" v-loading="loading" border>
        <el-table-column label="提交时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="essayTypeDisplayName" label="作文类型" min-width="150" />
        <el-table-column label="版本" width="80">
          <template #default="{ row }">v{{ row.versionNo || 1 }}</template>
        </el-table-column>
        <el-table-column prop="taskPromptSummary" label="题目摘要" min-width="180" show-overflow-tooltip />
        <el-table-column prop="wordCount" label="作文字数" width="100" />
        <el-table-column prop="nativeScoreDisplay" label="原生分" width="110">
          <template #default="{ row }">{{ row.nativeScoreDisplay || '-' }}</template>
        </el-table-column>
        <el-table-column prop="normalizedScore" label="换算分" width="100">
          <template #default="{ row }">
            {{ row.normalizedScore != null ? `${Math.round(row.normalizedScore)}/100` : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="gradeLabel" label="等级" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.gradeLabel" :type="gradeTagType(row.gradeLabel)" size="small">{{ row.gradeLabel }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="confidenceLevel" label="置信度" width="100">
          <template #default="{ row }">{{ row.confidenceLevel || '-' }}</template>
        </el-table-column>
        <el-table-column prop="scoringStatus" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.scoringStatus)" size="small">
              {{ statusLabel(row.scoringStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="aiModel" label="模型" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.aiModel || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="viewDetail(row.essayId)">
              查看详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        @current-change="loadHistory"
        class="pagination"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getHistory } from '@/api/essay'
import type { EssayHistoryItem, EssayTypeCode } from '@/types'
import { ESSAY_TYPE_OPTIONS } from '@/types'

const router = useRouter()
const loading = ref(false)
const history = ref<EssayHistoryItem[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const typeFilter = ref<EssayTypeCode | ''>('')
const statusFilter = ref('')
const essayTypeOptions = ESSAY_TYPE_OPTIONS

const filteredHistory = computed(() => {
  return history.value.filter(item => {
    if (typeFilter.value && item.essayType !== typeFilter.value) return false
    if (statusFilter.value && item.scoringStatus !== statusFilter.value) return false
    return true
  })
})

onMounted(() => {
  loadHistory()
})

async function loadHistory() {
  loading.value = true
  try {
    const data = await getHistory(currentPage.value - 1, pageSize.value)
    history.value = data.content
    total.value = data.totalElements
  } catch (error) {
    console.error('加载失败:', error)
  } finally {
    loading.value = false
  }
}

function viewDetail(id: number) {
  router.push(`/result/${id}`)
}

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleString('zh-CN')
}

function statusLabel(status: string) {
  if (status === 'COMPLETED') return '已完成'
  if (status === 'SCORING') return '评分中'
  if (status === 'PENDING') return '等待评分'
  if (status === 'FAILED') return '失败'
  return status
}

function statusTagType(status: string) {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'SCORING' || status === 'PENDING') return 'warning'
  return 'info'
}

function gradeTagType(label: string) {
  if (['优秀', '高分段'].includes(label)) return 'success'
  if (['良好'].includes(label)) return 'primary'
  if (['中等', '合格'].includes(label)) return 'warning'
  if (['及格', '基础'].includes(label)) return 'info'
  return 'danger'
}
</script>

<style scoped>
.history-view {
  padding: 20px;
}

.header-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.filters {
  display: flex;
  gap: 10px;
}

.pagination {
  margin-top: 20px;
  justify-content: center;
}
</style>
