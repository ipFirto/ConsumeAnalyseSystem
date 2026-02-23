package com.iptnet.consume.mapper;


import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;

@Mapper
public interface ProductMapper {

//    获取单个商品信息
    @Select("SELECT\n" +
            "  p.id,\n" +
            "  p.platform_id,\n" +
            "  p.brand,\n" +
            "  p.product_name,\n" +
            "  pl.`name` AS platform_name,\n" +
            "  p.category,\n" +
            "  p.amount,\n" +
            "  p.stock,\n" +
            "  p.`status`,\n" +
            "  p.created_at,\n" +
            "  p.updated_at\n" +
            "FROM product p\n" +
            "LEFT JOIN platform pl\n" +
            "  ON pl.id = p.platform_id\n" +
            "WHERE p.id = #{productId};")
    HashMap<String,Object> getOneProduct(Integer productId);

//    产商品列表，第一次调用全量数据，后续调用新数据，旧数据前端存localstorage，即使刷新浏览器也不会丢失
    @Select("SELECT\n" +
            "  p.id,\n" +
            "  p.platform_id,\n" +
            "  p.brand,\n" +
            "  p.product_name,\n" +
            "  pl.`name` AS platform_name,\n" +
            "  p.category,\n" +
            "  p.amount,\n" +
            "  p.stock,\n" +
            "  p.`status`,\n" +
            "  p.created_at,\n" +
            "  p.updated_at\n" +
            "FROM product p\n" +
            "LEFT JOIN platform pl\n" +
            "  ON pl.id = p.platform_id\n" +
            "WHERE p.`status` = #{status}\n" +
            "  AND p.platform_id = #{platformId}\n" +
            "ORDER BY p.created_at, p.id\n" +
            "LIMIT #{limit} OFFSET 0;")
    List<HashMap<String,Object>> listAll(Integer platformId,Integer status,Integer limit);

    @Select("SELECT\n" +
            "  p.id,\n" +
            "  p.platform_id,\n" +
            "  p.brand,\n" +
            "  p.product_name,\n" +
            "  pl.`name` AS platform_name,\n" +
            "  p.category,\n" +
            "  p.amount,\n" +
            "  p.stock,\n" +
            "  p.`status`,\n" +
            "  p.created_at,\n" +
            "  p.updated_at\n" +
            "FROM product p\n" +
            "LEFT JOIN platform pl\n" +
            "  ON pl.id = p.platform_id\n" +
            "WHERE p.`status` = #{status}\n" +
            "  AND p.platform_id = #{platformId}\n" +
            "ORDER BY p.created_at, p.id\n" +
            "LIMIT #{limit} OFFSET #{offset};")
    List<HashMap<String,Object>> listByPlatform(Integer platformId,Integer status,Integer limit,Integer offset);

    @Select("SELECT\n" +
            "  p.id,\n" +
            "  p.platform_id,\n" +
            "  p.brand,\n" +
            "  p.product_name,\n" +
            "  pl.`name` AS platform_name,\n" +
            "  p.category,\n" +
            "  p.amount,\n" +
            "  p.stock,\n" +
            "  p.`status`,\n" +
            "  p.created_at,\n" +
            "  p.updated_at\n" +
            "FROM product p\n" +
            "LEFT JOIN platform pl\n" +
            "  ON pl.id = p.platform_id\n" +
            "WHERE p.`status` = #{status}\n" +
            "  AND p.platform_id = #{platformId}\n" +
            "  AND p.id > #{productId}\n" +
            "ORDER BY p.created_at, p.id\n" +
            "LIMIT #{limit} OFFSET #{offset};")
    List<HashMap<String,Object>> listNew(Integer platformId,Integer productId,Integer status,Integer limit,Integer offset);



    @Insert("INSERT INTO product(platform_id,brand,product_name,category,amount,stock,created_at,updated_at) VALUES(#{platformId},#{brand},#{productName},#{category},#{amount},0,NOW(),NOW());")
    Boolean insert(@Param("platformId") Integer platformId,
                   @Param("brand") String brand,
                   @Param("productName") String productName,
                   @Param("amount") BigDecimal amount,
                   @Param("category") String category);

    @Update("UPDATE product SET status = #{status}, updated_at = NOW() WHERE id = #{productId}")
    Integer delete(@Param("status") Integer status, @Param("productId") Integer productId);

    @Update("UPDATE product SET " +
            "product_name = #{productName}, " +
            "brand = #{brand}, " +
            "category = #{category}, " +
            "updated_at = NOW() " +
            "WHERE id = #{productId}")
    Integer update(@Param("productName") String productName,
               @Param("brand") String brand,
               @Param("category") String category,
               @Param("productId") Integer productId);

    @Select("select category from product WHERE platform_id=#{platformId} GROUP BY category;")
    List<HashMap<String,Object>> showCategory(Integer platformId);

    @Select("SELECT id,name from platform ORDER BY id;")
    List<HashMap<String,Object>> showPlatform();



}
