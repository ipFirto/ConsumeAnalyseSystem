package com.iptnet.consume.service.analytics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface EnterpriseAnalyticsService {

    Map<String, Object> overview();

    List<HashMap<String, Object>> trend(int days);

    List<HashMap<String, Object>> platformRank(int limit);

    List<HashMap<String, Object>> provinceRank(int limit);

    List<HashMap<String, Object>> categoryRank(int limit);

    List<HashMap<String, Object>> paymentChannelRank(int limit);
}
