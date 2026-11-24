
package com.campustrade.dto;

import java.time.LocalDateTime;

public record AdminUserResponse(
        Long id,
        String email,
        String nickname,
        String role,
        String status,
        Boolean emailVerified,
        LocalDateTime createdAt
) {
}
