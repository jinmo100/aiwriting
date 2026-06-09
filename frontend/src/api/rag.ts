import request from './request'
import type { RagFeedback, RagIndexStatus } from '@/types'

export function getMyRagIndexStatus(embeddingConfigId?: number) {
  return request.get<RagIndexStatus>('/rag/index/my-status', {
    params: { embeddingConfigId }
  })
}

export function rebuildMyRagIndex(embeddingConfigId?: number, force = false) {
  return request.post<RagIndexStatus>('/rag/index/rebuild-my', {
    embeddingConfigId,
    force
  })
}

export function getRagFeedback(essayId: number) {
  return request.get<RagFeedback>(`/rag/feedbacks/${essayId}`)
}

export function generateRagFeedback(essayId: number, embeddingConfigId?: number) {
  return request.post<RagFeedback>(`/rag/feedbacks/${essayId}/generate`, {
    embeddingConfigId
  })
}

export function retryRagFeedback(essayId: number, embeddingConfigId?: number) {
  return request.post<RagFeedback>(`/rag/feedbacks/${essayId}/retry`, {
    embeddingConfigId
  })
}
