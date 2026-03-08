import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { EssayScoreResponse } from '@/types'

export const useEssayStore = defineStore('essay', () => {
  const currentResult = ref<EssayScoreResponse | null>(null)
  const loading = ref(false)

  function setCurrentResult(result: EssayScoreResponse) {
    currentResult.value = result
  }

  function clearResult() {
    currentResult.value = null
  }

  return {
    currentResult,
    loading,
    setCurrentResult,
    clearResult
  }
})
