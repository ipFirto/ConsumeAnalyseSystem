package com.iptnet.consume.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ConsumeSeedMapper {

    @Select("SELECT user_id FROM `user`")
    List<Integer> allUserIds();

    @Select("SELECT id FROM product")
    List<Long> allProductIds();

    @Select("SELECT id FROM city")
    List<Long> allCityIds();

    @Select("select id FROM platform")
    List<Long> allPlatformIds();
}
