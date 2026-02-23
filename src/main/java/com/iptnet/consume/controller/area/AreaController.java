package com.iptnet.consume.controller.area;

import com.iptnet.consume.common.Result;
import com.iptnet.consume.mapper.AreaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/area")
@RequiredArgsConstructor
public class AreaController {

    private final AreaMapper areaMapper;

    @GetMapping("/options")
    public Result options() {
        List<HashMap<String, Object>> provinces = areaMapper.listProvinces();
        List<HashMap<String, Object>> cities = areaMapper.listCities();

        Map<String, Object> data = new HashMap<>();
        data.put("provinces", provinces);
        data.put("cities", cities);
        return Result.success(data);
    }
}
