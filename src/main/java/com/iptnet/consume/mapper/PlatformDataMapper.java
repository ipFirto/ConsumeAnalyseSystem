package com.iptnet.consume.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.HashMap;
import java.util.List;

@Mapper
public interface PlatformDataMapper {

    @Select("SELECT co.id AS co_id,co.order_no AS co_order_no ,p.id AS product_id,p.brand AS p_brand,p.category AS category,p.product_name as product_name,pf.`name` as pf_name,co.amount AS amount,co.remark AS co_remark,co.created_at AS co_created_at,c.id AS c_id,c.`name` as c_name,c.`code` AS c_code,pr.id AS pr_id,pr.`name` as pr_name,pr.type as pr_type,u.user_id AS user_id,u.user_email AS user_email,u.user_name AS user_name,u.phone AS u_phone,u.`status` AS u_status FROM consumption_order co JOIN product p ON p.id = co.product_id JOIN city c ON c.id = co.city_id JOIN province pr ON pr.id = c.province_id JOIN `user` u on u.user_id = co.user_id AND u.`status` = 1 JOIN platform pf ON pf.id = p.platform_id WHERE p.platform_id = #{platformId} AND co.`status` IN (1, 2) ORDER BY co.id desc;")
    List<HashMap<String, Object>> platformData(Integer platformId);

    @Select("select * from platform")
    List<HashMap<String, Object>> platformList();

    @Select("SELECT id FROM platform WHERE status = 1 ORDER BY id")
    List<Integer> platformIds();

}
