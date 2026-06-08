<template>
  <div class="profile-view">
    <el-card>
      <template #header>
        <div class="header-title">
          <span>个人中心</span>
          <el-tag v-if="authStore.currentUser" type="success">{{ authStore.currentUser.role }}</el-tag>
        </div>
      </template>

      <el-descriptions v-if="authStore.currentUser" :column="1" border>
        <el-descriptions-item label="用户名">{{ authStore.currentUser.username }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ authStore.currentUser.email }}</el-descriptions-item>
        <el-descriptions-item label="显示名称">{{ authStore.currentUser.displayName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="账号状态">{{ authStore.currentUser.status }}</el-descriptions-item>
      </el-descriptions>

      <div class="actions">
        <el-button type="primary" @click="router.push('/config')">管理模型配置</el-button>
        <el-button @click="router.push('/history')">查看历史记录</el-button>
        <el-button type="danger" :loading="loggingOut" @click="handleLogout">退出登录</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/useAuthStore'

const router = useRouter()
const authStore = useAuthStore()
const loggingOut = ref(false)

async function handleLogout() {
  loggingOut.value = true
  try {
    await authStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  } finally {
    loggingOut.value = false
  }
}
</script>

<style scoped>
.profile-view {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
}

.header-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.actions {
  margin-top: 20px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
