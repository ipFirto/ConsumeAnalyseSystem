package com.iptnet.consume.service.province;

import java.util.HashMap;
import java.util.List;

public interface ProvinceService {

    List<HashMap<String, Object>> provinceData(Integer provinceId,Integer cityId);

    List<HashMap<String, Object>> getProvinceToCityTable(Integer provinceId);

}
