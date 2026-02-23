package com.iptnet.consume.controller.platform;

import com.iptnet.consume.common.Result;
import com.iptnet.consume.service.platform.PlatformService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/platform")
public class PlatformController {

    @Autowired
    private PlatformService platformService;


    @GetMapping("/{platformId}")
    public Result getPlatformData(@PathVariable("platformId") Integer platformId) {
        List<HashMap<String, Object>> objectHashMap = platformService.platformData(platformId);
//        System.out.println("objectHashMap = " + objectHashMap);
        return Result.success(objectHashMap);
    }
    @GetMapping("/list")
    public Result getPlatformList() {
        List<HashMap<String, Object>> hashMaps = platformService.platformList();
        return Result.success(hashMaps);
    }
}
