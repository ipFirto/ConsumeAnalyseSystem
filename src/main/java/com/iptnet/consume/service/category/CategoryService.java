package com.iptnet.consume.service.category;

import java.util.HashMap;
import java.util.List;

public interface CategoryService {

    List<HashMap<String, Object>> getCategoryData(Integer platformId, String categoryName);

}
