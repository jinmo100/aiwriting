import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ApiConfig } from '@/types'
import { getConfigs } from '@/api/config'

export const useConfigStore = defineStore('config', () => {
  const configs = ref<ApiConfig[]>([])
  const defaultConfig = ref<ApiConfig | null>(null)
  const loading = ref(false)

  // 加载所有配置
  async function loadConfigs() {
    loading.value = true
    try {
      configs.value = await getConfigs()
      defaultConfig.value = configs.value.find(c => c.isDefault) || null
    } finally {
      loading.value = false
    }
  }

  return {
    configs,
    defaultConfig,
    loading,
    loadConfigs
  }
})
