import request from './request'
import type {
  ApiConfig,
  ApiConfigRequest,
  ConfigSecurityPolicy,
  ProviderModelsFetchRequest,
  ProviderModelsResponse,
  ProviderTestRequest,
  ProviderTestResponse
} from '@/types'

// 获取所有配置
export function getConfigs() {
  return request.get<ApiConfig[]>('/configs')
}

// 获取单个配置
export function getConfig(id: number) {
  return request.get<ApiConfig>(`/configs/${id}`)
}

// 获取配置页安全策略
export function getConfigSecurityPolicy() {
  return request.get<ConfigSecurityPolicy>('/configs/security-policy')
}

// 创建配置
export function createConfig(data: ApiConfigRequest) {
  return request.post<ApiConfig>('/configs', data)
}

// 更新配置
export function updateConfig(id: number, data: ApiConfigRequest) {
  return request.put<ApiConfig>(`/configs/${id}`, data)
}

// 删除配置
export function deleteConfig(id: number) {
  return request.delete(`/configs/${id}`)
}

// 设置默认配置
export function setDefaultConfig(id: number) {
  return request.put(`/configs/${id}/default`)
}

// 显示完整 API Key（仅 dev/允许环境）
export function revealApiKey(id: number) {
  return request.post<{ apiKey: string }>(`/configs/${id}/reveal-api-key`)
}

// 未保存配置获取模型列表
export function fetchModels(data: ProviderModelsFetchRequest) {
  return request.post<ProviderModelsResponse, ProviderModelsFetchRequest>('/configs/models/fetch', data)
}

// 已保存配置获取模型列表
export function fetchModelsByConfig(id: number, forceRefresh = false) {
  return request.post<ProviderModelsResponse>(`/configs/${id}/models/fetch`, undefined, {
    params: { forceRefresh }
  })
}

// 未保存配置测试连接
export function testConnection(data: ProviderTestRequest) {
  return request.post<ProviderTestResponse, ProviderTestRequest>('/configs/test-connection', data)
}

// 已保存配置测试连接
export function testConnectionByConfig(id: number) {
  return request.post<ProviderTestResponse>(`/configs/${id}/test-connection`)
}

// 未保存配置测试结构化输出
export function testStructuredOutput(data: ProviderTestRequest) {
  return request.post<ProviderTestResponse, ProviderTestRequest>('/configs/test-structured-output', data)
}

// 已保存配置测试结构化输出
export function testStructuredOutputByConfig(id: number) {
  return request.post<ProviderTestResponse>(`/configs/${id}/test-structured-output`)
}
