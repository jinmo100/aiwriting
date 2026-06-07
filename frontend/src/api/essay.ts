import request from './request'
import type { EssaySubmitRequest, EssayScoreResponse, EssayHistoryItem, PageResponse } from '@/types'

export function submitEssay(data: EssaySubmitRequest) {
  return request.post<EssayScoreResponse, EssaySubmitRequest>('/essays/submit', data)
}

export function getHistory(page: number = 0, size: number = 10) {
  return request.get<PageResponse<EssayHistoryItem>>('/essays/history', {
    params: { page, size }
  })
}

export function getEssayDetail(id: number) {
  return request.get<EssayScoreResponse>(`/essays/${id}`)
}
