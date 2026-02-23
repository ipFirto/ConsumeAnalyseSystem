package com.iptnet.consume.controller.analytics;

import com.iptnet.consume.common.Result;
import com.iptnet.consume.service.analytics.EnterpriseAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class EnterpriseAnalyticsController {

    private final EnterpriseAnalyticsService enterpriseAnalyticsService;

    @GetMapping("/overview")
    public Result overview() {
        return Result.success(enterpriseAnalyticsService.overview());
    }

    @GetMapping("/trend")
    public Result trend(@RequestParam(defaultValue = "30") Integer days) {
        return Result.success(enterpriseAnalyticsService.trend(days == null ? 30 : days));
    }

    @GetMapping("/rank/platform")
    public Result platformRank(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(enterpriseAnalyticsService.platformRank(limit == null ? 10 : limit));
    }

    @GetMapping("/rank/province")
    public Result provinceRank(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(enterpriseAnalyticsService.provinceRank(limit == null ? 10 : limit));
    }

    @GetMapping("/rank/category")
    public Result categoryRank(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(enterpriseAnalyticsService.categoryRank(limit == null ? 10 : limit));
    }

    @GetMapping("/rank/payment-channel")
    public Result paymentChannelRank(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(enterpriseAnalyticsService.paymentChannelRank(limit == null ? 10 : limit));
    }
}
