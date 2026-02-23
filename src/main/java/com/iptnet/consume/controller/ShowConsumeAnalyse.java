package com.iptnet.consume.controller;

import com.iptnet.consume.common.Result;
import com.iptnet.consume.dao.User;
import com.iptnet.consume.dto.ConsumeOrderDTO;
import com.iptnet.consume.dto.UserInfo;
import com.iptnet.consume.service.consume.ConsumeService;
import com.iptnet.consume.utils.ListUtils;
import com.iptnet.consume.utils.ThreadLocalUtil;
import jakarta.servlet.ServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/show")
public class ShowConsumeAnalyse {

    @Autowired
    private ConsumeService consumeService;

    @Autowired
    private RedisTemplate<Object,Object> redisTemplate;
    @Qualifier("httpServletRequest")
    @Autowired
    private ServletRequest httpServletRequest;

    @GetMapping("/platformData")
    public Result rankByProvinceAndPlatform(@RequestParam Integer platformId) {
        // 设置订单id字符串(绑定平台id)
        Map<String,Object> map = ThreadLocalUtil.get();
        String user_email = map.get("email").toString();
        String newOrderId = "newOrderId"+platformId+user_email;
        // 判断redis中订单id字符串是否存在，不存在则调用所有数据，存在则调用新数据
        if (!redisTemplate.hasKey(newOrderId)) {
            // 不存在，先查询所有数据
            List<ConsumeOrderDTO> provinceProductList = consumeService.orderByOrigen(platformId);
            // 将数据最新的订单id存入redis，设置过期时间，避免查询用户过多redis内存oom
            redisTemplate.opsForValue().set(newOrderId,provinceProductList.getFirst().getCoId(),5, TimeUnit.MINUTES);
            return Result.success(provinceProductList);
        }else {
            String oldIdStr = redisTemplate.opsForValue().get(newOrderId).toString();
            Integer oldId = Integer.parseInt(oldIdStr);
            // redis中存在订单id字符串，则用订单id字符串调用最新数据
            List<ConsumeOrderDTO> newOrders = consumeService.orderNewByOrigen(platformId, oldId);
            //将新数据中的最新订单id重新赋值给redis，同时刷新过期时间
            if (newOrders.isEmpty()) {
                return Result.fail("暂未获取到最新订单");
            }
            redisTemplate.opsForValue().set(newOrderId,newOrders.getFirst().getCoId(),5, TimeUnit.MINUTES);
            return Result.success(newOrders);
        }
    }

    @GetMapping("/showBarByPlatform")
    public Result showBarByPlatform() {
        String str = consumeService.showBarByPlatform().getFirst();
        List<Integer> consumeByPlatform = ListUtils.parseIntList(str);
        return Result.success(consumeByPlatform);
    }

    @GetMapping("/showLineByPlatform")
    public Result showLineByPlatform(@RequestParam Integer platformId) {
        List<Map<String,Object>> orderInfoByPlatformId = consumeService.showLineByPlatform(platformId);
        return Result.success(orderInfoByPlatformId);
    }

    @GetMapping("/showSmoothByOriginal")
    public Result showSmoothByOriginal() {
        List<Map<String,Object>> orderInfoByProvince = consumeService.showSmoothByOriginal();
        return Result.success(orderInfoByProvince);
    }

    @GetMapping("/userOrder")
    public Result userOrder() {
        Map<String,Object> map = ThreadLocalUtil.get();
        Integer id = (Integer)map.get("id");
        List<Map<String, Object>> maps = consumeService.showUserOrder(id);
        return Result.success(maps);
    }



}
