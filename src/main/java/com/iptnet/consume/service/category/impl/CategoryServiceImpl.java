package com.iptnet.consume.service.category.impl;

import com.iptnet.consume.mapper.CategoryDataMapper;
import com.iptnet.consume.service.category.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    CategoryDataMapper categoryDataMapper;

    @Override
    public List<HashMap<String, Object>> getCategoryData(Integer platformId, String categoryName) {
        return categoryDataMapper.getCategoryData(platformId,categoryName);
    }
}
