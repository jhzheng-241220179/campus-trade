
package com.campustrade.dto;

public record CsrfTokenResponse(
        String token,
        String headerName
) {
}
