package com.iptnet.consume.service.product.impl;

import com.iptnet.consume.mapper.ProductMapper;
import com.iptnet.consume.service.product.ProductService;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;


    @Override
    public HashMap<String, Object> getOneProduct(Integer productId) {
        return productMapper.getOneProduct(productId);
    }

    @Override
    public List<HashMap<String, Object>> listAll(Integer platformId, Integer status, Integer limit) {
        return productMapper.listAll(platformId, status, limit);
    }

    @Override
    public List<HashMap<String, Object>> listByPlatform(Integer platformId, Integer status, Integer limit, Integer offset) {
        return productMapper.listByPlatform(platformId, status, limit, offset);
    }

    @Override
    public List<HashMap<String, Object>> listNew(Integer platformId, Integer productId, Integer status, Integer limit, Integer offset) {
        return productMapper.listNew(platformId, productId, status, limit, offset);
    }

    @Override
    public Boolean insert(Integer platformId, String brand, String productName, BigDecimal amount,String category) {
        return productMapper.insert(platformId, brand, productName, amount, category);
    }

    @Override
    public Integer delete(Integer status, Integer productId) {
        return productMapper.delete(status, productId);
    }

    @Override
    public Integer update(String productName, String brand, String category, Integer productId) {
        return productMapper.update(productName, brand, category, productId);
    }

    @Override
    public List<HashMap<String, Object>> showCategory(Integer platformId) {
        return productMapper.showCategory(platformId);
    }

    @Override
    public List<HashMap<String, Object>> showPlatform() {
        return productMapper.showPlatform();
    }
}
