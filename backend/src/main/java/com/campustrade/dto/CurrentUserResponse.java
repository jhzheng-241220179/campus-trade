
package com.campustrade.dto;

public record CurrentUserResponse(
        Long id,
        String nickname,
        String email,
        String role,
        boolean emailVerified
) {
}
