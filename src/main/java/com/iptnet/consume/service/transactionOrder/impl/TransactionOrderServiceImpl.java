package com.iptnet.consume.service.transactionOrder.impl;

import com.iptnet.consume.dao.User;
import com.iptnet.consume.dto.OrderEvent;
import com.iptnet.consume.mapper.TransactionOrderMapper;
import com.iptnet.consume.mapper.UserMapper;
import com.iptnet.consume.service.dashboard.DashboardCacheService;
import com.iptnet.consume.service.rocketmq.impl.MqProducer;
import com.iptnet.consume.service.transactionOrder.TransactionOrderService;
import com.iptnet.consume.utils.SnowflakeIdGenerator;
import com.iptnet.consume.utils.ThreadLocalUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
@Service
public class TransactionOrderServiceImpl implements TransactionOrderService {

    private static final Set<String> SUPPORTED_PAY_METHODS = Set.of("MOCK", "ALIPAY", "WECHAT", "BANKCARD");

    private final TransactionOrderMapper transactionOrderMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final UserMapper userMapper;
    private final MqProducer mqProducer;
    private final DashboardCacheService dashboardCacheService;

    @Value("${business.order.payment-timeout-seconds:120}")
    private long paymentTimeoutSeconds;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<HashMap<String, Object>> insertOrder(List<OrderEvent> orders) {
        if (orders == null || orders.isEmpty()) {
            throw new RuntimeException("No order to submit");
        }

        Integer uid = currentUid();
        checkUserExists(uid);

        List<String> createdOrderNos = new ArrayList<>();
        for (OrderEvent order : orders) {
            Integer productId = order == null ? null : order.getProductId();
            Integer cityId = order == null ? null : order.getCityId();
            Integer quantity = order == null ? null : order.getQuantity();
            BigDecimal amount = order == null ? null : order.getAmount();
            String remark = order == null ? null : order.getRemark();

            if (productId == null || productId <= 0) {
                throw new RuntimeException("Invalid product");
            }
            if (cityId == null || cityId <= 0) {
                throw new RuntimeException("Invalid city");
            }
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Invalid amount");
            }

            int safeQuantity = normalizeQuantity(quantity);
            int stockRows = transactionOrderMapper.deductProductStock(productId, safeQuantity);
            if (stockRows != 1) {
                throw new RuntimeException("Insufficient stock: " + productId);
            }

            String orderNo = String.valueOf(idGenerator.nextId());
            LocalDateTime createdAt = LocalDateTime.now();
            LocalDateTime payDeadline = createdAt.plusSeconds(Math.max(30L, paymentTimeoutSeconds));

            int rows = transactionOrderMapper.insertOrder(
                    uid,
                    orderNo,
                    productId,
                    cityId,
                    safeQuantity,
                    amount,
                    safeRemark(remark),
                    createdAt,
                    payDeadline
            );
            if (rows != 1) {
                throw new RuntimeException("Create order failed: " + orderNo);
            }

            transactionOrderMapper.insertOrderOperateLog(
                    orderNo,
                    uid,
                    "CREATE_ORDER",
                    null,
                    1,
                    "USER",
                    String.valueOf(uid),
                    "Create order from cart checkout, quantity=" + safeQuantity
            );

            mqProducer.sendDelayCloseOrder(orderNo);
            createdOrderNos.add(orderNo);
        }

        if (!createdOrderNos.isEmpty()) {
            mqProducer.sendDashboardAggregate(uid, "CREATE_ORDER");
        }

        return transactionOrderMapper.selectOrdersByNos(uid, createdOrderNos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer deleteCartItem(Integer userId, Integer productId, Integer cityId) {
        if (userId == null || userId <= 0 || productId == null || productId <= 0) {
            throw new RuntimeException("Invalid cart delete params");
        }

        int declineRows = transactionOrderMapper.declineCartItem(userId, productId, cityId);
        int deleteRows = transactionOrderMapper.deleteCartItem(userId, productId, cityId);

        if (declineRows <= 0 && deleteRows <= 0) {
            throw new RuntimeException("Cart item not found");
        }
        return declineRows + deleteRows;
    }

    @Override
    public List<HashMap<String, Object>> selectAllItem(Integer uid) {
        checkUserExists(uid);
        return transactionOrderMapper.selectAllItem(uid);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cartItem(Integer userId, Integer productId, Integer cityId, BigDecimal amount) {
        if (userId == null || userId <= 0) {
            throw new RuntimeException("Invalid user");
        }
        if (productId == null || productId <= 0) {
            throw new RuntimeException("Invalid product");
        }
        if (cityId == null || cityId <= 0) {
            throw new RuntimeException("Invalid city");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Invalid amount");
        }

        int rows = transactionOrderMapper.cartItem(userId, productId, cityId, amount);
        if (rows <= 0) {
            throw new RuntimeException("Add cart item failed");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<HashMap<String, Object>> updateOrder(List<OrderEvent> orders) {
        if (orders == null || orders.isEmpty()) {
            throw new RuntimeException("No order selected for pay");
        }

        closeExpiredOrders();

        Integer uid = currentUid();
        List<String> successOrderNos = new ArrayList<>();

        for (OrderEvent order : orders) {
            String orderNo = order == null ? "" : safeTrim(order.getOrderNo());
            if (orderNo.isEmpty()) {
                throw new RuntimeException("Order no is empty");
            }

            HashMap<String, Object> meta = transactionOrderMapper.selectOrderMeta(orderNo);
            if (meta == null || meta.isEmpty()) {
                throw new RuntimeException("Order not found: " + orderNo);
            }

            int orderUserId = asInt(meta.get("user_id"));
            if (orderUserId != uid) {
                throw new RuntimeException("No permission to pay this order: " + orderNo);
            }

            int status = asInt(meta.get("status"));
            if (status == 2) {
                successOrderNos.add(orderNo);
                continue;
            }
            if (status == 3) {
                throw new RuntimeException("Order already timeout: " + orderNo);
            }
            if (status == 4) {
                throw new RuntimeException("Order already canceled: " + orderNo);
            }
            if (status != 1) {
                throw new RuntimeException("Order status not payable: " + orderNo);
            }

            LocalDateTime paidAt = LocalDateTime.now();
            LocalDateTime payDeadline = asLocalDateTime(meta.get("pay_deadline"));
            if (payDeadline != null && paidAt.isAfter(payDeadline)) {
                int closeRows = transactionOrderMapper.closeIfUnpaid(orderNo);
                if (closeRows == 1) {
                    int productId = asInt(meta.get("product_id"));
                    int quantity = normalizeQuantity(meta.get("quantity"));
                    if (productId > 0) {
                        transactionOrderMapper.releaseProductStock(productId, quantity);
                    }
                    transactionOrderMapper.insertOrderOperateLog(
                            orderNo,
                            uid,
                            "TIMEOUT_CLOSE",
                            1,
                            3,
                            "SYSTEM",
                            "timeout-check",
                            "Order timeout during pay, quantity=" + quantity
                    );
                    mqProducer.sendDashboardAggregate(uid, "TIMEOUT_CLOSE");
                }
                throw new RuntimeException("Order timeout: " + orderNo);
            }

            String paymentMethod = normalizePayMethod(order == null ? null : order.getPaymentMethod());
            String paymentNo = String.valueOf(idGenerator.nextId());

            int payRows = transactionOrderMapper.payIfUnpaid(uid, orderNo, paymentMethod, paymentNo, paidAt);
            if (payRows != 1) {
                HashMap<String, Object> latestMeta = transactionOrderMapper.selectOrderMeta(orderNo);
                int latestStatus = latestMeta == null ? -1 : asInt(latestMeta.get("status"));
                if (latestStatus == 2) {
                    successOrderNos.add(orderNo);
                    continue;
                }
                if (latestStatus == 3) {
                    throw new RuntimeException("Order timeout: " + orderNo);
                }
                if (latestStatus == 4) {
                    throw new RuntimeException("Order canceled: " + orderNo);
                }
                throw new RuntimeException("Order pay failed: " + orderNo);
            }

            BigDecimal payAmount = asBigDecimal(meta.get("amount"));
            transactionOrderMapper.insertPaymentRecord(
                    paymentNo,
                    orderNo,
                    uid,
                    payAmount,
                    paymentMethod,
                    1,
                    paidAt,
                    "manual-pay",
                    "success"
            );

            transactionOrderMapper.insertOrderOperateLog(
                    orderNo,
                    uid,
                    "PAY_ORDER",
                    1,
                    2,
                    "USER",
                    String.valueOf(uid),
                    "Pay success by " + paymentMethod
            );

            successOrderNos.add(orderNo);
        }

        if (!successOrderNos.isEmpty()) {
            mqProducer.sendDashboardAggregate(uid, "PAY_ORDER");
        }

        return transactionOrderMapper.selectOrdersByNos(uid, successOrderNos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HashMap<String, Object> cancelOrder(Integer uid, String orderNo) {
        checkUserExists(uid);

        String safeOrderNo = safeTrim(orderNo);
        if (safeOrderNo.isEmpty()) {
            throw new RuntimeException("Order no is empty");
        }

        closeExpiredOrders();

        HashMap<String, Object> meta = transactionOrderMapper.selectOrderMeta(safeOrderNo);
        if (meta == null || meta.isEmpty()) {
            throw new RuntimeException("Order not found: " + safeOrderNo);
        }

        int orderUserId = asInt(meta.get("user_id"));
        if (orderUserId != uid) {
            throw new RuntimeException("No permission to cancel this order: " + safeOrderNo);
        }

        int status = asInt(meta.get("status"));
        if (status == 2) {
            throw new RuntimeException("Paid order cannot be canceled: " + safeOrderNo);
        }
        if (status == 3) {
            throw new RuntimeException("Order already timeout: " + safeOrderNo);
        }
        if (status == 4) {
            HashMap<String, Object> alreadyCanceled = transactionOrderMapper.selectOneOrder(uid, safeOrderNo);
            if (alreadyCanceled == null || alreadyCanceled.isEmpty()) {
                throw new RuntimeException("Order not found");
            }
            return alreadyCanceled;
        }
        if (status != 1) {
            throw new RuntimeException("Order status not cancelable: " + safeOrderNo);
        }

        LocalDateTime canceledAt = LocalDateTime.now();
        int cancelRows = transactionOrderMapper.cancelIfUnpaid(uid, safeOrderNo, canceledAt);
        if (cancelRows != 1) {
            HashMap<String, Object> latestMeta = transactionOrderMapper.selectOrderMeta(safeOrderNo);
            int latestStatus = latestMeta == null ? -1 : asInt(latestMeta.get("status"));
            if (latestStatus == 2) {
                throw new RuntimeException("Order already paid: " + safeOrderNo);
            }
            if (latestStatus == 3) {
                throw new RuntimeException("Order already timeout: " + safeOrderNo);
            }
            if (latestStatus == 4) {
                HashMap<String, Object> alreadyCanceled = transactionOrderMapper.selectOneOrder(uid, safeOrderNo);
                if (alreadyCanceled == null || alreadyCanceled.isEmpty()) {
                    throw new RuntimeException("Order not found");
                }
                return alreadyCanceled;
            }
            throw new RuntimeException("Cancel order failed: " + safeOrderNo);
        }

        int productId = asInt(meta.get("product_id"));
        int quantity = normalizeQuantity(meta.get("quantity"));
        if (productId > 0) {
            transactionOrderMapper.releaseProductStock(productId, quantity);
        }

        transactionOrderMapper.insertOrderOperateLog(
                safeOrderNo,
                uid,
                "CANCEL_ORDER",
                1,
                4,
                "USER",
                String.valueOf(uid),
                "Cancel unpaid order, quantity=" + quantity
        );

        mqProducer.sendDashboardAggregate(uid, "CANCEL_ORDER");

        HashMap<String, Object> order = transactionOrderMapper.selectOneOrder(uid, safeOrderNo);
        if (order == null || order.isEmpty()) {
            throw new RuntimeException("Order not found");
        }
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HashMap<String, Object> selectOneOrder(Integer uid, String orderNo) {
        if (uid == null || uid <= 0) {
            throw new RuntimeException("Invalid user");
        }
        String safeOrderNo = safeTrim(orderNo);
        if (safeOrderNo.isEmpty()) {
            throw new RuntimeException("Order no is empty");
        }

        closeExpiredOrders();

        HashMap<String, Object> order = transactionOrderMapper.selectOneOrder(uid, safeOrderNo);
        if (order == null || order.isEmpty()) {
            throw new RuntimeException("Order not found");
        }
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<HashMap<String, Object>> selectAllOrders(Integer uid) {
        checkUserExists(uid);
        closeExpiredOrders();
        return transactionOrderMapper.selectAllOrders(uid);
    }

    @Override
    public List<HashMap<String, Object>> selectRecentOrders(Integer uid, Integer limit, Integer status) {
        checkUserExists(uid);

        int safeLimit = limit == null ? 30 : Math.max(1, Math.min(500, limit));
        Integer safeStatus = null;
        if (status != null && status >= 1 && status <= 4) {
            safeStatus = status;
        }

        if (safeLimit == 30 && safeStatus == null) {
            List<HashMap<String, Object>> cached = dashboardCacheService.getRecentOrders(uid);
            if (cached != null && !cached.isEmpty()) {
                return cached;
            }
        }

        List<HashMap<String, Object>> rows = transactionOrderMapper.selectRecentOrders(uid, safeLimit, safeStatus);
        if (safeLimit == 30 && safeStatus == null) {
            dashboardCacheService.saveRecentOrders(uid, rows);
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int closeExpiredOrders() {
        LocalDateTime now = LocalDateTime.now();
        List<HashMap<String, Object>> expiredOrders = transactionOrderMapper.selectExpiredUnpaidOrders(now, 500);
        if (expiredOrders == null || expiredOrders.isEmpty()) {
            return 0;
        }

        Map<Integer, Integer> releaseCounter = new HashMap<>();
        int closedRows = 0;

        for (HashMap<String, Object> orderMeta : expiredOrders) {
            if (orderMeta == null || orderMeta.isEmpty()) continue;
            String orderNo = safeTrim(String.valueOf(orderMeta.get("order_no")));
            if (orderNo.isEmpty()) continue;

            int closeRows = transactionOrderMapper.closeIfUnpaid(orderNo);
            if (closeRows != 1) {
                continue;
            }

            closedRows += 1;

            int productId = asInt(orderMeta.get("product_id"));
            int releaseQuantity = normalizeQuantity(orderMeta.get("quantity"));
            if (productId > 0) {
                releaseCounter.merge(productId, releaseQuantity, Integer::sum);
            }

            int userId = asInt(orderMeta.get("user_id"));
            transactionOrderMapper.insertOrderOperateLog(
                    orderNo,
                    userId,
                    "TIMEOUT_CLOSE",
                    1,
                    3,
                    "JOB",
                    "timeout-scan",
                    "Order timeout by scheduled scan, quantity=" + releaseQuantity
            );
        }

        for (Map.Entry<Integer, Integer> entry : releaseCounter.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0) continue;
            transactionOrderMapper.releaseProductStock(entry.getKey(), entry.getValue());
        }

        if (closedRows > 0) {
            mqProducer.sendDashboardAggregate(null, "TIMEOUT_SCAN");
        }
        return closedRows;
    }

    private Integer currentUid() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        if (claims == null || claims.get("id") == null) {
            throw new RuntimeException("Login expired");
        }
        Object idObj = claims.get("id");
        if (idObj instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(idObj));
    }

    private void checkUserExists(Integer uid) {
        if (uid == null || uid <= 0) {
            throw new RuntimeException("Invalid user");
        }
        User user = userMapper.findById(uid);
        if (user == null) {
            throw new RuntimeException("User not found: " + uid);
        }
    }

    private String normalizePayMethod(String rawMethod) {
        String value = safeTrim(rawMethod).toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "MOCK";
        }
        if (!SUPPORTED_PAY_METHODS.contains(value)) {
            return "MOCK";
        }
        return value;
    }

    private String safeRemark(String remark) {
        String text = safeTrim(remark);
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() <= 500) {
            return text;
        }
        return text.substring(0, 500);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private int normalizeQuantity(Object raw) {
        int quantity = asInt(raw);
        if (quantity <= 0) {
            return 1;
        }
        return Math.min(quantity, 10000);
    }

    private int asInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private LocalDateTime asLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return LocalDateTime.parse(String.valueOf(value).replace(" ", "T"));
    }
}
