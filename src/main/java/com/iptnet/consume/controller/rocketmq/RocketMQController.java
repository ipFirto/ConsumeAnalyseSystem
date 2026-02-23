package com.iptnet.consume.controller.rocketmq;

import com.iptnet.consume.common.Result;
import com.iptnet.consume.dto.OrderEvent;
import com.iptnet.consume.dto.ToItemReq;
import com.iptnet.consume.service.product.ProductService;
import com.iptnet.consume.service.transactionOrder.TransactionOrderService;
import com.iptnet.consume.utils.ThreadLocalUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
@RequestMapping("/mq")
@RestController
public class RocketMQController {

    private final TransactionOrderService transactionOrderService;
    private final ProductService productService;

    @PostMapping("/addOrders")
    public Result addOrders(@RequestBody List<OrderEvent> orders) {
        List<HashMap<String, Object>> createdOrders = transactionOrderService.insertOrder(orders);
        return Result.success(createdOrders);
    }

    @PostMapping("/toItem")
    public Result toItem(@RequestBody ToItemReq req) {
        Integer pid = req == null ? null : req.getPid();
        Integer cityId = req == null ? null : req.getCityId();
        if (pid == null || pid <= 0) {
            throw new RuntimeException("商品参数不正确");
        }
        if (cityId == null || cityId <= 0) {
            throw new RuntimeException("城市参数不正确");
        }

        HashMap<String, Object> oneProduct = productService.getOneProduct(pid);
        if (oneProduct == null || oneProduct.isEmpty()) {
            throw new RuntimeException("商品不存在");
        }

        BigDecimal amount = toBigDecimal(oneProduct.get("amount"));
        Integer uid = currentUid();
        transactionOrderService.cartItem(uid, pid, cityId, amount);
        return Result.success("添加购物车成功");
    }

    @GetMapping("/itemList")
    public Result itemList() {
        Integer uid = currentUid();
        List<HashMap<String, Object>> items = transactionOrderService.selectAllItem(uid);
        return Result.success(items);
    }

    @GetMapping("/deleteItem")
    public Result deleteItem(@RequestParam Integer productId,
                             @RequestParam(required = false) Integer cityId) {
        Integer uid = currentUid();
        Integer rows = transactionOrderService.deleteCartItem(uid, productId, cityId);
        return Result.success("购物车更新成功: " + rows);
    }

    @PostMapping("/orderSubmit")
    public Result orderSubmit(@RequestBody List<OrderEvent> orderEvents) {
        Integer uid = currentUid();
        List<HashMap<String, Object>> createdOrders = transactionOrderService.insertOrder(orderEvents);

        Set<String> seen = new HashSet<>();
        if (orderEvents != null) {
            for (OrderEvent order : orderEvents) {
                if (order == null || order.getProductId() == null || order.getProductId() <= 0) {
                    continue;
                }
                String key = order.getProductId() + "::" + (order.getCityId() == null ? "0" : order.getCityId());
                if (!seen.add(key)) {
                    continue;
                }
                int clearTimes = order.getQuantity() == null || order.getQuantity() <= 0 ? 1 : Math.min(order.getQuantity(), 10000);
                try {
                    for (int i = 0; i < clearTimes; i++) {
                        transactionOrderService.deleteCartItem(uid, order.getProductId(), order.getCityId());
                    }
                } catch (Exception ignore) {
                    // Item may already be absent from cart; keep order submission successful.
                }
            }
        }

        return Result.success(createdOrders);
    }

    @PostMapping("/paySubmit")
    public Result paySubmit(@RequestBody List<OrderEvent> orders) {
        List<HashMap<String, Object>> paidOrders = transactionOrderService.updateOrder(orders);
        return Result.success(paidOrders);
    }

    @PostMapping("/cancelOrder")
    public Result cancelOrder(@RequestParam String orderNo) {
        Integer uid = currentUid();
        HashMap<String, Object> canceledOrder = transactionOrderService.cancelOrder(uid, orderNo);
        return Result.success(canceledOrder);
    }

    @GetMapping("/selectAllOrders")
    public Result<List<HashMap<String, Object>>> selectAllOrder() {
        Integer uid = currentUid();
        List<HashMap<String, Object>> orders = transactionOrderService.selectAllOrders(uid);
        return Result.success(orders);
    }

    @GetMapping("/selectRecentOrders")
    public Result<List<HashMap<String, Object>>> selectRecentOrders(
            @RequestParam(defaultValue = "30") Integer limit,
            @RequestParam(required = false) Integer status
    ) {
        Integer uid = currentUid();
        List<HashMap<String, Object>> orders = transactionOrderService.selectRecentOrders(uid, limit, status);
        return Result.success(orders);
    }

    @GetMapping("/selectOneOrder")
    public Result<HashMap<String, Object>> selectOneOrder(@RequestParam String orderNo) {
        Integer uid = currentUid();
        HashMap<String, Object> order = transactionOrderService.selectOneOrder(uid, orderNo);
        return Result.success(order);
    }

    private Integer currentUid() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        if (claims == null || claims.get("id") == null) {
            throw new RuntimeException("登录状态失效，请重新登录");
        }
        Object idObj = claims.get("id");
        if (idObj instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(idObj));
    }

    private BigDecimal toBigDecimal(Object value) {
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
}
