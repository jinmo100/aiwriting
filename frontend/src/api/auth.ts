import request from './request'
import type { AuthResponse, LoginRequest, RegisterRequest } from '@/types'

export function register(data: RegisterRequest) {
  return request.post<AuthResponse, RegisterRequest>('/auth/register', data)
}

export function login(data: LoginRequest) {
  return request.post<AuthResponse, LoginRequest>('/auth/login', data)
}

export function logout() {
  return request.post<void>('/auth/logout')
}

export function getCurrentUser() {
  return request.get<AuthResponse>('/auth/me')
}
