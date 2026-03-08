// API配置相关类型
export interface ApiConfig {
  id: number
  configName: string
  provider: string
  baseUrl: string
  modelName: string
  isDefault: boolean
  createdAt: string
  updatedAt: string
}

export interface ApiConfigRequest {
  configName: string
  provider: string
  baseUrl: string
  apiKey: string
  modelName: string
  isDefault?: boolean
}

// 作文相关类型
export interface Essay {
  id: number
  content: string
  wordCount: number
  essayType: string
  createdAt: string
}

export interface EssaySubmitRequest {
  content: string
  essayType?: string
  configId?: number
}

export interface ScoreDetail {
  overallScore: number
  contentScore: number
  languageScore: number
  structureScore: number
  coherenceScore: number
  strengths: string[]
  suggestions: string[]
  errors: ErrorDetail[]
  detailedFeedback: string
}

export interface ErrorDetail {
  sentence: string
  type: string
  description: string
  correction: string
}

export interface EssayScoreResponse {
  essayId: number
  score: ScoreDetail
  processingTime: number
}

// API响应类型
export interface ApiResponse<T> {
  success: boolean
  code: string
  message: string
  data: T
  timestamp: string
}

// 分页响应
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  currentPage: number
}
