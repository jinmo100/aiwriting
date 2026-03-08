import request from './request'
import type { EssaySubmitRequest, EssayScoreResponse, Essay, PageResponse } from '@/types'

// 提交作文并评分
export function submitEssay(data: EssaySubmitRequest) {
  return request.post<EssayScoreResponse>('/essays/submit', data)
}

// 获取历史记录
export function getHistory(page: number = 0, size: number = 10) {
  return request.get<PageResponse<Essay>>('/essays/history', {
    params: { page, size }
  })
}

// 获取作文详情
export function getEssayDetail(id: number) {
  return request.get<{ essay: Essay; score: any }>(`/essays/${id}`)
}
