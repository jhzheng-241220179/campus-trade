package com.campustrade.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductCreateRequest(

        @NotNull(message = "卖家ID不能为空")
        @Positive(message = "卖家ID必须为正整数")
        Long sellerId,

        @NotBlank(message = "商品标题不能为空")
        @Size(max = 100, message = "商品标题不能超过100个字符")
        String title,

        @NotNull(message = "商品价格不能为空")
        @DecimalMin(value = "0.01", message = "商品价格不能低于0.01元")
        @Digits(integer = 8, fraction = 2, message = "商品价格最多8位整数、2位小数")
        BigDecimal price,

        @NotBlank(message = "商品分类不能为空")
        @Size(max = 50, message = "商品分类不能超过50个字符")
        String category,

        String description,

        @NotBlank(message = "商品成色不能为空")
        @Size(max = 20, message = "商品成色不能超过20个字符")
        String conditionLevel,

        @Size(max = 100, message = "联系方式不能超过100个字符")
        String contact

) {}