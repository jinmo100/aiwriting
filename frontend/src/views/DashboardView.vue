<template>
  <div class="dashboard-view">
    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>学习看板</span>
          <el-button size="small" @click="loadSummary">刷新</el-button>
        </div>
      </template>

      <el-empty v-if="!loading && summary && summary.totalEssays === 0" description="暂无作文记录">
        <el-button type="primary" @click="router.push('/submit')">提交第一篇作文</el-button>
      </el-empty>

      <template v-else-if="summary">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12" :md="6">
            <div class="metric-card">
              <div class="metric-title">总提交</div>
              <div class="metric-value">{{ summary.totalEssays }}</div>
            </div>
          </el-col>
          <el-col :xs="24" :sm="12" :md="6">
            <div class="metric-card">
              <div class="metric-title">已完成</div>
              <div class="metric-value">{{ summary.completedEssays }}</div>
            </div>
          </el-col>
          <el-col :xs="24" :sm="12" :md="6">
            <div class="metric-card">
              <div class="metric-title">平均分</div>
              <div class="metric-value">{{ scoreDisplay(summary.averageNormalizedScore) }}</div>
            </div>
          </el-col>
          <el-col :xs="24" :sm="12" :md="6">
            <div class="metric-card">
              <div class="metric-title">最高分</div>
              <div class="metric-value">{{ scoreDisplay(summary.bestNormalizedScore) }}</div>
            </div>
          </el-col>
        </el-row>

        <el-row :gutter="16" class="section-row">
          <el-col :xs="24" :md="12">
            <el-card shadow="never" class="inner-card">
              <template #header>近期提交</template>
              <div class="recent-grid">
                <div>
                  <div class="muted">近 7 天</div>
                  <strong>{{ summary.submissionsLast7Days }}</strong>
                </div>
                <div>
                  <div class="muted">近 30 天</div>
                  <strong>{{ summary.submissionsLast30Days }}</strong>
                </div>
                <div>
                  <div class="muted">近 90 天</div>
                  <strong>{{ summary.submissionsLast90Days }}</strong>
                </div>
              </div>
            </el-card>
          </el-col>
          <el-col :xs="24" :md="12">
            <el-card shadow="never" class="inner-card">
              <template #header>任务状态</template>
              <div class="status-list">
                <el-tag type="success">完成 {{ summary.completedEssays }}</el-tag>
                <el-tag type="warning">评分中 {{ summary.scoringEssays }}</el-tag>
                <el-tag type="danger">失败 {{ summary.failedEssays }}</el-tag>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <el-card shadow="never" class="inner-card">
          <template #header>作文类型分布</template>
          <el-table :data="summary.typeDistribution" border>
            <el-table-column prop="essayTypeDisplayName" label="作文类型" min-width="180" />
            <el-table-column prop="essayType" label="Code" width="180" />
            <el-table-column prop="count" label="数量" width="100" />
          </el-table>
        </el-card>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getDashboardSummary } from '@/api/dashboard'
import type { DashboardSummary } from '@/types'

const router = useRouter()
const loading = ref(false)
const summary = ref<DashboardSummary | null>(null)

onMounted(() => {
  loadSummary()
})

async function loadSummary() {
  loading.value = true
  try {
    summary.value = await getDashboardSummary()
  } catch (error) {
    console.error('加载学习看板失败:', error)
  } finally {
    loading.value = false
  }
}

function scoreDisplay(score?: number) {
  return score === undefined || score === null ? '-' : `${Math.round(score)}/100`
}
</script>

<style scoped>
.dashboard-view {
  padding: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.metric-card {
  padding: 18px 16px;
  border-radius: 10px;
  background: #f7f9fc;
  text-align: center;
}

.metric-title,
.muted {
  color: #909399;
}

.metric-value {
  margin-top: 8px;
  color: #303133;
  font-size: 28px;
  font-weight: 700;
}

.section-row,
.inner-card {
  margin-top: 16px;
}

.recent-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  text-align: center;
}

.recent-grid strong {
  display: block;
  margin-top: 6px;
  font-size: 22px;
}

.status-list {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
</style>
