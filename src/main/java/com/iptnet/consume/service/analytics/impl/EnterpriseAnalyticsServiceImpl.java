package com.iptnet.consume.service.analytics.impl;

import com.iptnet.consume.mapper.EnterpriseAnalyticsMapper;
import com.iptnet.consume.service.analytics.EnterpriseAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EnterpriseAnalyticsServiceImpl implements EnterpriseAnalyticsService {

    private final EnterpriseAnalyticsMapper enterpriseAnalyticsMapper;

    @Override
    public Map<String, Object> overview() {
        HashMap<String, Object> raw = enterpriseAnalyticsMapper.overview();

        long totalOrders = asLong(raw.get("total_orders"));
        long paidOrders = asLong(raw.get("paid_orders"));
        long unpaidOrders = asLong(raw.get("unpaid_orders"));
        long timeoutOrders = asLong(raw.get("timeout_orders"));

        BigDecimal grossAmount = asBigDecimal(raw.get("gross_amount"));
        BigDecimal paidAmount = asBigDecimal(raw.get("paid_amount"));
        BigDecimal unpaidAmount = asBigDecimal(raw.get("unpaid_amount"));
        BigDecimal paidAvgTicket = asBigDecimal(raw.get("paid_avg_ticket"));

        BigDecimal payRate = totalOrders == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(paidOrders)
                .divide(BigDecimal.valueOf(totalOrders), 4, RoundingMode.HALF_UP);

        BigDecimal avgOrderAmount = totalOrders == 0
                ? BigDecimal.ZERO
                : grossAmount.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);

        Map<String, Object> result = new HashMap<>();
        result.put("totalOrders", totalOrders);
        result.put("paidOrders", paidOrders);
        result.put("unpaidOrders", unpaidOrders);
        result.put("timeoutOrders", timeoutOrders);
        result.put("grossAmount", grossAmount);
        result.put("paidAmount", paidAmount);
        result.put("unpaidAmount", unpaidAmount);
        result.put("paidAvgTicket", paidAvgTicket);
        result.put("avgOrderAmount", avgOrderAmount);
        result.put("payRate", payRate);
        return result;
    }

    @Override
    public List<HashMap<String, Object>> trend(int days) {
        int safeDays = clamp(days, 1, 180);
        int lookbackDays = Math.max(0, safeDays - 1);
        return enterpriseAnalyticsMapper.trend(lookbackDays);
    }

    @Override
    public List<HashMap<String, Object>> platformRank(int limit) {
        return enterpriseAnalyticsMapper.platformRank(clamp(limit, 1, 100));
    }

    @Override
    public List<HashMap<String, Object>> provinceRank(int limit) {
        return enterpriseAnalyticsMapper.provinceRank(clamp(limit, 1, 100));
    }

    @Override
    public List<HashMap<String, Object>> categoryRank(int limit) {
        return enterpriseAnalyticsMapper.categoryRank(clamp(limit, 1, 100));
    }

    @Override
    public List<HashMap<String, Object>> paymentChannelRank(int limit) {
        return enterpriseAnalyticsMapper.paymentChannelRank(clamp(limit, 1, 100));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
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
}
