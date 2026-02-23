package com.iptnet.consume.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.HashMap;
import java.util.List;

@Mapper
public interface CategoryDataMapper {

    @Select("SELECT\n" +
            "  co.id AS co_id,\n" +
            "  co.order_no AS co_order_no,\n" +
            "  p.brand AS p_brand,\n" +
            "  p.product_name AS product_name,\n" +
            "  pf.`name` AS pf_name,\n" +
            "  co.amount AS amount,\n" +
            "  co.remark AS co_remark,\n" +
            "  co.created_at AS co_created_at,\n" +
            "  c.id AS c_id,\n" +
            "  c.`name` AS c_name,\n" +
            "  c.`code` AS c_code,\n" +
            "  pr.id AS pr_id,\n" +
            "  pr.`name` AS pr_name,\n" +
            "  pr.type AS pr_type,\n" +
            "  u.user_id AS user_id,\n" +
            "  u.user_email AS user_email,\n" +
            "  u.user_name AS user_name,\n" +
            "  u.phone AS u_phone,\n" +
            "  u.`status` AS u_status\n" +
            "FROM\n" +
            "  consumption_order co\n" +
            "  JOIN product p ON p.id = co.product_id\n" +
            "  JOIN city c ON c.id = co.city_id\n" +
            "  JOIN province pr ON pr.id = c.province_id\n" +
            "  JOIN `user` u ON u.user_id = co.user_id AND u.`status` = 1\n" +
            "  JOIN platform pf ON pf.id = p.platform_id\n" +
            "WHERE\n" +
            "  p.platform_id = #{platformId} and p.category=#{categoryName} AND co.`status` IN (1, 2) ORDER BY co.id desc;")
    List<HashMap<String, Object>> getCategoryData(
            @Param("platformId") Integer platformId,
            @Param("categoryName") String categoryName
    );


}
