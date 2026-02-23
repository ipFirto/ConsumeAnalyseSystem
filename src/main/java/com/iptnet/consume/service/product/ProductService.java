package com.iptnet.consume.service.product;

import com.iptnet.consume.mapper.ProductMapper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;

public interface ProductService {



    HashMap<String, Object> getOneProduct(Integer productId);


    List<HashMap<String, Object>> listAll(Integer platformId, Integer status, Integer limit);

    List<HashMap<String, Object>> listByPlatform(Integer platformId, Integer status, Integer limit, Integer offset);


    List<HashMap<String, Object>> listNew(Integer platformId, Integer productId, Integer status, Integer limit, Integer offset);


    Boolean insert(Integer platformId, String brand, String productName, BigDecimal amount,String category);


    Integer delete(Integer status, Integer productId);


    Integer update(String productName, String brand, String category, Integer productId);


    List<HashMap<String, Object>> showCategory(Integer platformId);


    List<HashMap<String, Object>> showPlatform();
}
