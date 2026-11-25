
import { api } from './http'
import type { ApiResponse } from './http'

export type ReservationRoundStatus =
  | 'OPEN'
  | 'FROZEN'
  | 'MATCHING'
  | 'COMPLETED'
  | 'CANCELLED'

export type ReservationStatus =
  | 'ACTIVE'
  | 'INVITED'
  | 'ACCEPTED'
  | 'DECLINED'
  | 'EXPIRED'
  | 'CANCELLED'

export interface ReservationResponse {
  productId: number
  roundId: number | null
  roundStatus: ReservationRoundStatus | null

  firstReservedAt: string | null
  priorityEndsAt: string | null
  coolingEndsAt: string | null
  sellerResponseDeadline: string | null

  activeReservationCount: number

  reservationId: number | null
  reservationStatus: ReservationStatus | null
  reservationOrder: number | null
  queuePosition: number | null

  canReserve: boolean
  canCancel: boolean

  serverTime: string
}

export async function getReservation(
  productId: number,
): Promise<ReservationResponse> {
  const response =
    await api.get<ApiResponse<ReservationResponse>>(
      `/products/${productId}/reservations/mine`,
    )

  return response.data.data
}

export async function createReservation(
  productId: number,
): Promise<ReservationResponse> {
  const response =
    await api.post<ApiResponse<ReservationResponse>>(
      `/products/${productId}/reservations`,
    )

  return response.data.data
}

export async function cancelReservation(
  productId: number,
): Promise<ReservationResponse> {
  const response =
    await api.delete<ApiResponse<ReservationResponse>>(
      `/products/${productId}/reservations/mine`,
    )

  return response.data.data
}
