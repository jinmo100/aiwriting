import request from './request'
import type { EmbeddingConfig, EmbeddingConfigRequest, EmbeddingTestResponse } from '@/types'

export function getEmbeddingConfigs() {
  return request.get<EmbeddingConfig[]>('/embedding-configs')
}

export function createEmbeddingConfig(data: EmbeddingConfigRequest) {
  return request.post<EmbeddingConfig, EmbeddingConfigRequest>('/embedding-configs', data)
}

export function updateEmbeddingConfig(id: number, data: EmbeddingConfigRequest) {
  return request.put<EmbeddingConfig, EmbeddingConfigRequest>(`/embedding-configs/${id}`, data)
}

export function deleteEmbeddingConfig(id: number) {
  return request.delete(`/embedding-configs/${id}`)
}

export function setDefaultEmbeddingConfig(id: number) {
  return request.put(`/embedding-configs/${id}/default`)
}

export function testUnsavedEmbeddingConfig(data: EmbeddingConfigRequest) {
  return request.post<EmbeddingTestResponse, EmbeddingConfigRequest>('/embedding-configs/test', data)
}

export function testSavedEmbeddingConfig(id: number) {
  return request.post<EmbeddingTestResponse>(`/embedding-configs/${id}/test`)
}
