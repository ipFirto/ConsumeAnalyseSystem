package com.iptnet.consume.controller;

import com.iptnet.consume.common.Result;
import com.iptnet.consume.dao.User;
import com.iptnet.consume.service.consume.ConsumeService;
import com.iptnet.consume.service.user.UserService;
import com.iptnet.consume.utils.SnowflakeIdGenerator;
import com.iptnet.consume.utils.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

//@RestController
//@RequestMapping("/consume")
//@Validated
public class ConsumeController {

//    @Autowired
    private SnowflakeIdGenerator idGenerator;
//    @Autowired
    private ConsumeService consumeService;
//    @Autowired
    private UserService userService;

    private static final SecureRandom RANDOM = new SecureRandom();

    @PostMapping("/insert")
    public Result insertOrderAuto() {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer uid = (Integer) map.get("id");

        User user = userService.findById(uid);
        if (user == null) {
            return Result.fail("用户不存在");
        }

        int productId = ThreadLocalRandom.current().nextInt(1, 1877);
        int cityId = ThreadLocalRandom.current().nextInt(1, 343);

        int cents = ThreadLocalRandom.current().nextInt(10, 2_000_000 + 1);
        BigDecimal amount = BigDecimal.valueOf(cents).movePointLeft(2);

        String orderNo = String.valueOf(idGenerator.nextId());
        String remark = "自动生成测试订单";

        // ✅ 先写Redis队列
//        consumeService.enqueueOrder(uid, orderNo, productId, cityId, amount, remark);

        return Result.success(orderNo);
    }

}
