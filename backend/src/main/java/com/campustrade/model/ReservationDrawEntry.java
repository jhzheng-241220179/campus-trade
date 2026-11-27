
package com.campustrade.model;

public record ReservationDrawEntry(

        Long drawId,
        Long roundId,
        Long reservationId,
        int reservationOrder,
        int drawWeight,
        int queuePosition

) {
}
