<template>
  <div class="nav-bar">
    <el-menu
      class="main-menu"
      :default-active="activeIndex"
      mode="horizontal"
      @select="handleSelect"
      background-color="#545c64"
      text-color="#fff"
      active-text-color="#ffd04b"
    >
      <el-menu-item index="/submit">
        <el-icon><Edit /></el-icon>
        <span>提交作文</span>
      </el-menu-item>
      <el-menu-item index="/config">
        <el-icon><Setting /></el-icon>
        <span>API配置</span>
      </el-menu-item>
      <el-menu-item index="/embedding-config">
        <el-icon><Connection /></el-icon>
        <span>Embedding 配置</span>
      </el-menu-item>
      <el-menu-item index="/history">
        <el-icon><Document /></el-icon>
        <span>历史记录</span>
      </el-menu-item>
      <el-menu-item index="/dashboard">
        <el-icon><TrendCharts /></el-icon>
        <span>学习看板</span>
      </el-menu-item>
    </el-menu>

    <div class="auth-actions">
      <template v-if="!authStore.isAuthenticated">
        <el-button text @click="router.push('/login')">
          <el-icon><User /></el-icon>
          登录
        </el-button>
        <el-button type="primary" size="small" @click="router.push('/register')">
          <el-icon><UserFilled /></el-icon>
          注册
        </el-button>
      </template>
      <template v-else>
        <el-button text @click="router.push('/profile')">
          <el-icon><User /></el-icon>
          {{ authStore.currentUser?.displayName || authStore.currentUser?.username }}
        </el-button>
        <el-button text @click="handleLogout">退出</el-button>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Edit, Setting, Document, User, UserFilled, TrendCharts, Connection } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/useAuthStore'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const activeIndex = computed(() => route.path)

function handleSelect(index: string) {
  router.push(index)
}

async function handleLogout() {
  await authStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
.nav-bar {
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  background: #545c64;
}

.main-menu {
  border-bottom: none;
}

.auth-actions {
  flex: 1;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  padding-right: 16px;
}

.auth-actions :deep(.el-button.is-text) {
  color: #fff;
}
</style>
