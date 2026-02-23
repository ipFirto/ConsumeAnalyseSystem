package com.iptnet.consume.service.platform;

import java.util.HashMap;
import java.util.List;

public interface PlatformService {

    List<HashMap<String, Object>> platformData(Integer platformId);

    List<HashMap<String, Object>> platformList();

}
