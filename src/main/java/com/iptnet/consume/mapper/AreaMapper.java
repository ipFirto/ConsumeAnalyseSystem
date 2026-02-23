package com.iptnet.consume.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.HashMap;
import java.util.List;

@Mapper
public interface AreaMapper {

    @Select("SELECT id, name FROM province WHERE status = 1 ORDER BY name")
    List<HashMap<String, Object>> listProvinces();

    @Select("SELECT id, name, province_id FROM city WHERE status = 1 ORDER BY province_id, name")
    List<HashMap<String, Object>> listCities();
}
