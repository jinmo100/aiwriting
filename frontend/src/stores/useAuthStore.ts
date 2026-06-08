import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getCurrentUser, login as loginApi, logout as logoutApi, register as registerApi } from '@/api/auth'
import type { AuthUser, LoginRequest, RegisterRequest } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const currentUser = ref<AuthUser | null>(null)
  const initialized = ref(false)
  const loading = ref(false)

  const isAuthenticated = computed(() => Boolean(currentUser.value))
  const isAdmin = computed(() => currentUser.value?.role === 'ADMIN')

  async function fetchMe(force = false) {
    if (initialized.value && !force) return currentUser.value
    loading.value = true
    try {
      const response = await getCurrentUser()
      currentUser.value = response.authenticated ? response.user : null
      initialized.value = true
      return currentUser.value
    } finally {
      loading.value = false
    }
  }

  async function login(payload: LoginRequest) {
    const response = await loginApi(payload)
    currentUser.value = response.user
    initialized.value = true
    return response.user
  }

  async function register(payload: RegisterRequest) {
    const response = await registerApi(payload)
    currentUser.value = response.user
    initialized.value = true
    return response.user
  }

  async function logout() {
    await logoutApi()
    currentUser.value = null
    initialized.value = true
    sessionStorage.removeItem('essay-evaluator:pendingSubmission')
  }

  return {
    currentUser,
    initialized,
    loading,
    isAuthenticated,
    isAdmin,
    fetchMe,
    login,
    register,
    logout
  }
})
