package com.iptnet.consume.common;

import com.iptnet.consume.common.cache.IdCache;
import com.iptnet.consume.service.consume.ConsumeService;
import com.iptnet.consume.utils.SnowflakeIdGenerator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class AutoOrderJob {

    private final ConsumeService consumeService;
    private final SnowflakeIdGenerator idGenerator;
    private final IdCache idCache;

    public AutoOrderJob(ConsumeService consumeService,
                        SnowflakeIdGenerator idGenerator,
                        IdCache idCache) {
        this.consumeService = consumeService;
        this.idGenerator = idGenerator;
        this.idCache = idCache;
    }

//    @Scheduled(fixedRate = 500) // 每 1000ms 生成 1 单
    public void generateOneOrderPerSecond() {

        List<Integer> users = idCache.getUserIds();
        List<Long> products = idCache.getProductIds();
        List<Long> cities = idCache.getCityIds();
        List<Long> platforms = idCache.getPlatformIds();

        if (users.isEmpty() || products.isEmpty() || cities.isEmpty() || platforms.isEmpty()) {
            return; // 没数据就不生成
        }

        int uid = users.get(ThreadLocalRandom.current().nextInt(users.size()));
        long productId = products.get(ThreadLocalRandom.current().nextInt(products.size()));
        long cityId = cities.get(ThreadLocalRandom.current().nextInt(cities.size()));

        // 金额：0.10 ~ 20000.00，精确到分（避免浮点误差）
        int cents = ThreadLocalRandom.current().nextInt(10, 2_000_000 + 1);
        BigDecimal amount = BigDecimal.valueOf(cents).movePointLeft(2);

        String orderNo = String.valueOf(idGenerator.nextId());
        String remark = "系统每秒自动生成";

        // 注意：你的 mapper/service 参数类型如果是 Integer，就把 long 强转/改签名为 Long
        consumeService.autoInsert(uid, orderNo, (int) productId, (int) cityId, amount, remark);
    }
}