package com.iptnet.consume.mapper;


import com.iptnet.consume.dto.ConsumeOrderDTO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ConsumeMapper {

//    自动插入消费数据
    @Insert("INSERT INTO consumption_order (user_id, order_no, product_id, city_id, amount, remark) Values (#{user_id},#{order_no},#{product_id},#{city_id},#{amount},#{remark})")
    void insertConsume(Integer user_id, String order_no, Integer product_id, Integer city_id, BigDecimal amount, String remark);

//    按照地区id查询所有消费记录

    /**
     * @param platformId, 平台id
     * 请求方式http://localhost:8080/show/province/platform/rank?provinceId=13&platformId=1
     */
    @Select("SELECT co.id AS co_id,co.order_no AS co_order_no ,p.brand AS p_brand,p.product_name as product_name,pf.`name` as pf_name,co.amount AS amount,co.remark AS co_remark,co.created_at AS co_created_at,c.id AS c_id,c.`name` as c_name,c.`code` AS c_code,pr.id AS pr_id,pr.`name` as pr_name,pr.type as pr_type,u.user_id AS user_id,u.user_email AS user_email,u.user_name AS user_name,u.phone AS u_phone,u.`status` AS u_status FROM consumption_order co JOIN product p ON p.id = co.product_id JOIN city c ON c.id = co.city_id JOIN province pr ON pr.id = c.province_id JOIN `user` u on u.user_id = co.user_id AND u.`status` = 1 JOIN platform pf ON pf.id = p.platform_id WHERE p.platform_id = #{platformId} AND co.`status` IN (1, 2) ORDER BY co.id desc;")
    List<ConsumeOrderDTO> orderByOrigen(Integer platformId);

    @Select("SELECT co.id AS co_id,co.order_no AS co_order_no ,p.brand AS p_brand,p.product_name as product_name,pf.`name` as pf_name,co.amount AS amount,co.remark AS co_remark,co.created_at AS co_created_at,c.id AS c_id,c.`name` as c_name,c.`code` AS c_code,pr.id AS pr_id,pr.`name` as pr_name,pr.type as pr_type,u.user_id AS user_id,u.user_email AS user_email,u.user_name AS user_name,u.phone AS u_phone,u.`status` AS u_status FROM consumption_order co JOIN product p ON p.id = co.product_id JOIN city c ON c.id = co.city_id JOIN province pr ON pr.id = c.province_id JOIN `user` u on u.user_id = co.user_id AND u.`status` = 1 JOIN platform pf ON pf.id = p.platform_id WHERE p.platform_id = #{platformId} AND co.id > #{id} AND co.`status` IN (1, 2) ORDER BY co.id desc;")
    List<ConsumeOrderDTO> orderNewByOrigen(Integer platformId, Integer id);

//    平台消费数据
    @Select("SELECT JSON_ARRAY(SUM(CASE WHEN p.platform_id = 1 THEN 1 ELSE 0 END),SUM(CASE WHEN p.platform_id = 2 THEN 1 ELSE 0 END),SUM(CASE WHEN p.platform_id = 3 THEN 1 ELSE 0 END),SUM(CASE WHEN p.platform_id = 4 THEN 1 ELSE 0 END),SUM(CASE WHEN p.platform_id = 5 THEN 1 ELSE 0 END)) AS counts FROM consumption_order co JOIN product p ON p.id = co.product_id JOIN `user` u ON u.user_id = co.user_id AND u.`status` = 1 WHERE p.platform_id IN (1,2,3,4,5) AND co.`status` IN (1, 2);")
    List<String> showBarByPlatform();

//    类别消费数据
    @Select("SELECT pf.id,pf.`name` AS platform_name, p.category, COUNT(*) AS cnt FROM product p JOIN consumption_order co ON co.product_id = p.id JOIN `user` u ON u.user_id = co.user_id AND u.`status` = 1 JOIN platform pf ON p.platform_id = pf.id WHERE p.platform_id = #{platformId} AND co.`status` IN (1, 2) GROUP BY pf.`name`, p.category ORDER BY cnt DESC;")
    List<Map<String,Object>> showLineByPlatform(Integer platformId);

    @Select("SELECT co.id AS co_id,co.order_no AS co_order_no ,p.brand AS p_brand,p.product_name as product_name,pf.`name` as pf_name,co.amount AS amount,co.remark AS co_remark,co.created_at AS co_created_at,c.id AS c_id,c.`name` as c_name,c.`code` AS c_code,pr.id AS pr_id,pr.`name` as pr_name,pr.type as pr_type,u.user_id AS user_id,u.user_email AS user_email,u.user_name AS user_name,u.phone AS u_phone,u.`status` AS u_status FROM consumption_order co JOIN product p ON p.id = co.product_id JOIN city c ON c.id = co.city_id JOIN province pr ON pr.id = c.province_id JOIN `user` u on u.user_id = co.user_id JOIN platform pf ON pf.id = p.platform_id WHERE  co.user_id = #{id} ORDER BY co.id desc LIMIT 80;")
    List<Map<String,Object>> showUserOrder(Integer id);



//    @Select("SELECT\n" +
//            "  co.id,\n" +
//            "  co.order_no,\n" +
//            "  co.product_id,\n" +
//            "  co.amount,\n" +
//            "  co.remark,\n" +
//            "  co.created_at,\n" +
//            "  co.updated_at,\n" +
//            "  pr.`name`  AS province_name,\n" +
//            "  c.province_id,\n" +
//            "  c.id       AS city_id,\n" +
//            "  c.`name`   AS city_name\n" +
//            "FROM consumption_order AS co\n" +
//            "JOIN `user` AS u\n" +
//            "  ON u.user_id = co.user_id\n" +
//            " AND u.`status` = 1\n" +
//            "JOIN city AS c\n" +
//            "  ON c.id = co.city_id\n" +
//            " AND c.province_id = #{provinceId}\n" +
//            "JOIN province AS pr\n" +
//            "  ON pr.id = c.province_id\n" +
//            "WHERE co.`status` = 1;\n")

//    省消费数据
    @Select("SELECT pr.id AS province_id, pr.`name` AS province_name, SUM(t.cnt) AS order_total FROM (SELECT co.city_id, COUNT(*) AS cnt FROM consumption_order co JOIN `user` u ON u.user_id = co.user_id AND u.`status` = 1 WHERE co.`status` IN (1, 2) GROUP BY co.city_id) t JOIN city c ON c.id = t.city_id JOIN province pr ON pr.id = c.province_id GROUP BY pr.id, pr.`name` ORDER BY order_total DESC;")
    List<Map<String,Object>> showSmoothByOriginal();
}
