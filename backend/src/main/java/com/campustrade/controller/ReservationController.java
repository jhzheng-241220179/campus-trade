
package com.campustrade.controller;

import com.campustrade.common.ApiResponse;
import com.campustrade.dto.ReservationResponse;
import com.campustrade.security.CurrentUserIdResolver;
import com.campustrade.service.ReservationService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products/{productId}/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final CurrentUserIdResolver currentUserIdResolver;

    public ReservationController(
            ReservationService reservationService,
            CurrentUserIdResolver currentUserIdResolver
    ) {
        this.reservationService = reservationService;
        this.currentUserIdResolver = currentUserIdResolver;
    }

    @GetMapping("/mine")
    public ApiResponse<ReservationResponse> getReservation(
            @PathVariable Long productId,
            Authentication authentication
    ) {
        Long buyerId = currentUserIdResolver
                .requireCurrentUserId(authentication);

        return ApiResponse.success(
                reservationService.getReservation(
                        productId,
                        buyerId
                )
        );
    }

    @PostMapping
    public ApiResponse<ReservationResponse> reserve(
            @PathVariable Long productId,
            Authentication authentication
    ) {
        Long buyerId = currentUserIdResolver
                .requireCurrentUserId(authentication);

        return ApiResponse.success(
                reservationService.reserve(
                        productId,
                        buyerId
                )
        );
    }

    @DeleteMapping("/mine")
    public ApiResponse<ReservationResponse> cancel(
            @PathVariable Long productId,
            Authentication authentication
    ) {
        Long buyerId = currentUserIdResolver
                .requireCurrentUserId(authentication);

        return ApiResponse.success(
                reservationService.cancel(
                        productId,
                        buyerId
                )
        );
    }
}
