package com.iptnet.consume.service.platform.impl;

import com.iptnet.consume.mapper.PlatformDataMapper;
import com.iptnet.consume.service.platform.PlatformService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;

@Service
public class PlatformServiceImpl  implements PlatformService {

    @Autowired
    PlatformDataMapper platformDataMapper;


    @Override
    public List<HashMap<String, Object>> platformData(Integer platformId) {
        return platformDataMapper.platformData(platformId);
    }

    @Override
    public List<HashMap<String, Object>> platformList() {
        return platformDataMapper.platformList();
    }


}
