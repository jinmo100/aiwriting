<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <template #header>
        <div class="auth-header">
          <span>登录英作评析</span>
          <el-tag type="info">用户自带 API Key</el-tag>
        </div>
      </template>

      <el-alert
        title="登录后才能管理自己的模型配置、提交作文和查看历史记录。"
        type="info"
        show-icon
        :closable="false"
        class="mb-16"
      />

      <el-form :model="form" label-width="100px" @keyup.enter="handleLogin">
        <el-form-item label="用户名/邮箱" required>
          <el-input v-model="form.usernameOrEmail" autocomplete="username" placeholder="请输入用户名或邮箱" />
        </el-form-item>
        <el-form-item label="密码" required>
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="请输入密码"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleLogin">登录</el-button>
          <el-button @click="router.push('/register')">注册新账号</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/useAuthStore'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const loading = ref(false)

const form = ref({
  usernameOrEmail: '',
  password: ''
})

async function handleLogin() {
  if (!form.value.usernameOrEmail.trim() || !form.value.password) {
    ElMessage.warning('请填写用户名/邮箱和密码')
    return
  }
  loading.value = true
  try {
    await authStore.login({
      usernameOrEmail: form.value.usernameOrEmail.trim(),
      password: form.value.password
    })
    ElMessage.success('登录成功')
    router.push((route.query.redirect as string) || '/submit')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 70vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.auth-card {
  width: min(520px, 100%);
}

.auth-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.mb-16 {
  margin-bottom: 16px;
}
</style>
