package com.iptnet.consume.service.transactionOrder;

import com.iptnet.consume.dto.OrderEvent;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;

public interface TransactionOrderService {

    List<HashMap<String, Object>> insertOrder(List<OrderEvent> orders);

    Integer deleteCartItem(Integer userId, Integer productId, Integer cityId);

    List<HashMap<String, Object>> selectAllItem(Integer uid);

    void cartItem(Integer userId, Integer productId, Integer cityId, BigDecimal amount);

    List<HashMap<String, Object>> updateOrder(List<OrderEvent> orders);

    HashMap<String, Object> cancelOrder(Integer uid, String orderNo);

    HashMap<String, Object> selectOneOrder(Integer uid, String orderNo);

    List<HashMap<String, Object>> selectAllOrders(Integer uid);

    List<HashMap<String, Object>> selectRecentOrders(Integer uid, Integer limit, Integer status);

    int closeExpiredOrders();
}
