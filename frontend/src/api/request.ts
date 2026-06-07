import axios from 'axios'
import { ElMessage } from 'element-plus'
import type { AxiosRequestConfig } from 'axios'
import type { ApiResponse } from '@/types'

const axiosInstance = axios.create({
  baseURL: '/api',
  timeout: 120000 // 异步评分已落地；保留较长超时用于配置测试、模型列表和慢网络场景
})

// 请求拦截器
axiosInstance.interceptors.request.use(
  (config) => {
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
axiosInstance.interceptors.response.use(
  (response) => {
    const res = response.data as ApiResponse<any>

    if (!res.success) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }

    return res.data
  },
  (error) => {
    const message = error.response?.data?.message || error.message || '网络错误'
    ElMessage.error(message)
    return Promise.reject(new Error(message))
  }
)

const request = {
  get<T = unknown>(url: string, config?: AxiosRequestConfig) {
    return axiosInstance.get<ApiResponse<T>, T>(url, config)
  },

  post<T = unknown, D = unknown>(url: string, data?: D, config?: AxiosRequestConfig<D>) {
    return axiosInstance.post<ApiResponse<T>, T, D>(url, data, config)
  },

  put<T = unknown, D = unknown>(url: string, data?: D, config?: AxiosRequestConfig<D>) {
    return axiosInstance.put<ApiResponse<T>, T, D>(url, data, config)
  },

  delete<T = unknown>(url: string, config?: AxiosRequestConfig) {
    return axiosInstance.delete<ApiResponse<T>, T>(url, config)
  }
}

export default request
