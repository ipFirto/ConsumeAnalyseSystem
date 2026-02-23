package com.iptnet.consume.controller.province;


import com.iptnet.consume.common.Result;
import com.iptnet.consume.service.province.ProvinceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/province")
public class ProvinceController {

    @Autowired
    ProvinceService provinceService;

    @GetMapping("/{provinceId}/{cityId}")
    public Result provinceData(@PathVariable("provinceId")Integer provinceId, @PathVariable("cityId") Integer cityId) {
        List<HashMap<String, Object>> hashMaps = provinceService.provinceData(provinceId, cityId);
        return Result.success(hashMaps);
    }

    @GetMapping("/{provinceId}")
    public  Result getProvinceToCityTable(@PathVariable("provinceId")Integer provinceId){
        List<HashMap<String, Object>> hashMaps = provinceService.getProvinceToCityTable(provinceId);
        return Result.success(hashMaps);
    }

}
