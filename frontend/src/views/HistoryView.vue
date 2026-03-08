<template>
  <div class="history-view">
    <el-card>
      <template #header>
        <span>历史记录</span>
      </template>

      <el-table :data="history" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="作文内容" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.content.substring(0, 100) }}...
          </template>
        </el-table-column>
        <el-table-column prop="wordCount" label="字数" width="100" />
        <el-table-column prop="essayType" label="类型" width="100" />
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="viewDetail(row.id)">
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
        style="margin-top: 20px; text-align: center"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getHistory } from '@/api/essay'
import type { Essay } from '@/types'

const router = useRouter()
const loading = ref(false)
const history = ref<Essay[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

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
</script>

<style scoped>
.history-view {
  padding: 20px;
}
</style>
