
package com.campustrade.dto;

import com.campustrade.model.ReservationRoundStatus;
import com.campustrade.model.ReservationStatus;

import java.time.LocalDateTime;

public record ReservationResponse(

        Long productId,

        Long roundId,

        ReservationRoundStatus roundStatus,

        LocalDateTime firstReservedAt,

        LocalDateTime priorityEndsAt,

        LocalDateTime coolingEndsAt,

        LocalDateTime sellerResponseDeadline,

        int activeReservationCount,

        Long reservationId,

        ReservationStatus reservationStatus,

        Integer reservationOrder,

        Integer queuePosition,

        boolean canReserve,

        boolean canCancel,

        LocalDateTime serverTime

) {
}
