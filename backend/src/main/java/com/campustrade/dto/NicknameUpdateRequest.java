
package com.campustrade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NicknameUpdateRequest(
        @NotBlank(message = "昵称不能为空")
        @Size(max = 64, message = "昵称不能超过64个字符")
        String nickname
) {
}
