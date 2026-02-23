package com.iptnet.consume.service.province.impl;

import com.iptnet.consume.mapper.ProvinceDataMapper;
import com.iptnet.consume.service.province.ProvinceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;

@Service
public class ProvinceServiceImpl implements ProvinceService {

    @Autowired
    ProvinceDataMapper provinceDataMapper;


    @Override
    public List<HashMap<String, Object>> provinceData(Integer provinceId, Integer cityId) {
        return provinceDataMapper.provinceData(provinceId,cityId);
    }

    @Override
    public List<HashMap<String, Object>> getProvinceToCityTable(Integer provinceId) {
        return provinceDataMapper.getProvinceToCityTable(provinceId);
    }
}
