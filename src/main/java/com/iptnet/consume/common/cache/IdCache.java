package com.iptnet.consume.common.cache;

import com.iptnet.consume.mapper.ConsumeSeedMapper;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.List;

@Component
public class IdCache {

    private final ConsumeSeedMapper seedMapper;

    private volatile List<Integer> userIds = Collections.emptyList();
    private volatile List<Long> productIds = Collections.emptyList();
    private volatile List<Long> cityIds = Collections.emptyList();
    private volatile List<Long> platformIds = Collections.emptyList();

    public IdCache(ConsumeSeedMapper seedMapper) {
        this.seedMapper = seedMapper;
        reload();
    }

    // 你也可以做成 @Scheduled 每10分钟刷新一次
    public void reload() {
        this.userIds = seedMapper.allUserIds();
        this.productIds = seedMapper.allProductIds();
        this.cityIds = seedMapper.allCityIds();
        this.platformIds = seedMapper.allPlatformIds();
    }

    public List<Integer> getUserIds() { return userIds; }
    public List<Long> getProductIds() { return productIds; }
    public List<Long> getCityIds() { return cityIds; }
    public List<Long> getPlatformIds() { return platformIds; }
}
