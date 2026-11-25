
import { api } from './http'
import type { ApiResponse } from './http'

export interface Product {
  id: number
  sellerId: number
  title: string
  price: number
  category: string
  description: string
  conditionLevel: string
  status: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface OwnedProduct extends Product {
  contact: string
}

export interface ProductCreateRequest {
  title: string
  price: number
  category: string
  description: string
  conditionLevel: string
  contact: string
}

export interface ProductUpdateRequest
  extends ProductCreateRequest {
  version: number
}

export interface ProductImage {
  id: number
  productId: number
  url: string
  displayOrder: number
}

export async function getProducts(): Promise<Product[]> {
  const response =
    await api.get<ApiResponse<Product[]>>(
      '/products',
    )

  return response.data.data
}

export async function getProductById(
  id: number,
): Promise<Product> {
  const response =
    await api.get<ApiResponse<Product>>(
      `/products/${id}`,
    )

  return response.data.data
}

export async function getMyProducts():
  Promise<OwnedProduct[]> {
  const response =
    await api.get<ApiResponse<OwnedProduct[]>>(
      '/products/mine',
    )

  return response.data.data
}

export async function getMyDraft(
  id: number,
): Promise<OwnedProduct> {
  const response =
    await api.get<ApiResponse<OwnedProduct>>(
      `/products/mine/${id}`,
    )

  return response.data.data
}

export async function updateDraft(
  id: number,
  request: ProductUpdateRequest,
): Promise<void> {
  await api.put(`/products/${id}`, request)
}

export async function createProduct(
  request: ProductCreateRequest,
): Promise<number> {
  const response =
    await api.post<ApiResponse<number>>(
      '/products',
      request,
    )

  return response.data.data
}

export async function publishProduct(
  id: number,
): Promise<void> {
  await api.post(`/products/${id}/publish`)
}

export async function offShelfProduct(
  id: number,
): Promise<void> {
  await api.post(`/products/${id}/off-shelf`)
}

export async function getProductImages(
  productId: number,
): Promise<ProductImage[]> {
  const response =
    await api.get<ApiResponse<ProductImage[]>>(
      `/products/${productId}/images`,
    )

  return response.data.data
}

export async function uploadProductImage(
  productId: number,
  file: File,
): Promise<ProductImage> {
  const formData = new FormData()

  formData.append('file', file)

  const response =
    await api.post<ApiResponse<ProductImage>>(
      `/products/${productId}/images`,
      formData,
    )

  return response.data.data
}

export async function deleteProductImage(
  productId: number,
  imageId: number,
): Promise<void> {
  await api.delete(
    `/products/${productId}/images/${imageId}`,
  )
}
