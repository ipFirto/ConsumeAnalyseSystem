package com.iptnet.consume.config;

import com.iptnet.consume.service.dashboard.DashboardEventStore;
import com.iptnet.consume.service.dashboard.DashboardStreamService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class DashboardConfig {

    @Bean
    public DashboardEventStore dashboardEventStore() {
        return new DashboardEventStore(20000); // 保留 2w 条事件用于 delta
    }

    @Bean
    public DashboardStreamService dashboardStreamService(DashboardEventStore store) {
        return new DashboardStreamService(store, 0L); // 0=不超时（代理可能会断，前端可重连）
    }
}