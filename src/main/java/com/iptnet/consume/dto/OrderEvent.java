package com.iptnet.consume.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
@EqualsAndHashCode
public class OrderEvent {
    private Integer id;
    private Integer userId;
    private String orderNo;
    private Integer productId;
    private Integer cityId;
    private Integer quantity;
    private BigDecimal amount;
    private String remark;
    private String paymentMethod;
    private LocalDateTime createdAt;

    public OrderEvent(Integer id,
                      Integer userId,
                      String orderNo,
                      Integer productId,
                      Integer cityId,
                      Integer quantity,
                      BigDecimal amount,
                      String remark,
                      LocalDateTime createdAt) {
        this(id, userId, orderNo, productId, cityId, quantity, amount, remark, null, createdAt);
    }
}
