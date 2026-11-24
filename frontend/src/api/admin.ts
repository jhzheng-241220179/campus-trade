
import { api } from './http'
import type { ApiResponse } from './http'

export interface AdminUser {
  id: number
  email: string | null
  nickname: string
  role: string
  status: string
  emailVerified: boolean
  createdAt: string
}

export interface AdminProduct {
  id: number
  sellerId: number
  title: string
  price: number
  category: string
  status: string
  version: number
  createdAt: string
}

export async function getAdminUsers(): Promise<AdminUser[]> {
  const response = await api.get<ApiResponse<AdminUser[]>>(
    '/admin/users',
  )
  return response.data.data
}

export async function banUser(id: number): Promise<void> {
  await api.post(`/admin/users/${id}/ban`)
}

export async function unbanUser(id: number): Promise<void> {
  await api.post(`/admin/users/${id}/unban`)
}

export async function getAdminProducts(): Promise<AdminProduct[]> {
  const response = await api.get<ApiResponse<AdminProduct[]>>(
    '/admin/products',
  )
  return response.data.data
}

export async function forceOffShelfProduct(
  id: number,
): Promise<void> {
  await api.post(`/admin/products/${id}/off-shelf`)
}
