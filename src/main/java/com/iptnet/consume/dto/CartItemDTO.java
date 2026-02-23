package com.iptnet.consume.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @ Integer id,主键
 * @ Integer user_id,用户id
 * @ Integer product_id,产品id
 * @ Integer city_id,城市id
 * @ Integer status,购物车订单状态
 * @ LocalDateTime created_at,购物车订单创建时间
 * @ LocalDateTime updated_at,购物车订单更新时间
 */

@Data
@RequiredArgsConstructor
public class CartItemDTO {

    Integer id;
    @NotNull
    Integer user_id;
    @NotNull
    Integer product_id;
    @NotNull
    Integer city_id;
    @NotNull
    BigDecimal amount;
    @NotNull
    Integer status;
    @NotNull
    LocalDateTime created_at;
    LocalDateTime updated_at;



}
