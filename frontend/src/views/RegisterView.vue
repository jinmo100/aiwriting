<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <template #header>
        <div class="auth-header">
          <span>注册账号</span>
          <el-tag type="success">开放注册</el-tag>
        </div>
      </template>

      <el-alert
        title="注册后请先添加你的 AI Provider、模型和 API Key，再提交作文评分。"
        type="info"
        show-icon
        :closable="false"
        class="mb-16"
      />

      <el-form :model="form" label-width="100px" @keyup.enter="handleRegister">
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" autocomplete="username" placeholder="3-32 位用户名" />
        </el-form-item>
        <el-form-item label="邮箱" required>
          <el-input v-model="form.email" autocomplete="email" placeholder="用于登录和后续通知" />
        </el-form-item>
        <el-form-item label="显示名称">
          <el-input v-model="form.displayName" placeholder="可选，例如：小明" />
        </el-form-item>
        <el-form-item label="密码" required>
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="8-64 位，至少包含字母和数字"
          />
        </el-form-item>
        <el-form-item label="确认密码" required>
          <el-input
            v-model="confirmPassword"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="再次输入密码"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleRegister">注册并登录</el-button>
          <el-button @click="router.push('/login')">已有账号，去登录</el-button>
        </el-form-item>
      </el-form>
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
const loading = ref(false)
const confirmPassword = ref('')

const form = ref({
  username: '',
  email: '',
  displayName: '',
  password: ''
})

async function handleRegister() {
  if (!form.value.username.trim() || !form.value.email.trim() || !form.value.password) {
    ElMessage.warning('请填写用户名、邮箱和密码')
    return
  }
  if (form.value.password !== confirmPassword.value) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  loading.value = true
  try {
    await authStore.register({
      username: form.value.username.trim(),
      email: form.value.email.trim(),
      displayName: form.value.displayName.trim() || undefined,
      password: form.value.password
    })
    ElMessage.success('注册成功，请先添加模型配置')
    router.push('/config')
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
  width: min(560px, 100%);
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
