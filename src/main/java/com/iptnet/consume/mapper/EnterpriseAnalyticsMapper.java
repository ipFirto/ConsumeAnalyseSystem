package com.iptnet.consume.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.HashMap;
import java.util.List;

@Mapper
public interface EnterpriseAnalyticsMapper {

    @Select("""
        SELECT
          COUNT(*) AS total_orders,
          SUM(CASE WHEN co.status = 2 THEN 1 ELSE 0 END) AS paid_orders,
          SUM(CASE WHEN co.status = 1 THEN 1 ELSE 0 END) AS unpaid_orders,
          SUM(CASE WHEN co.status = 3 THEN 1 ELSE 0 END) AS timeout_orders,
          COALESCE(SUM(co.amount), 0) AS gross_amount,
          COALESCE(SUM(CASE WHEN co.status = 2 THEN co.amount ELSE 0 END), 0) AS paid_amount,
          COALESCE(SUM(CASE WHEN co.status = 1 THEN co.amount ELSE 0 END), 0) AS unpaid_amount,
          COALESCE(AVG(CASE WHEN co.status = 2 THEN co.amount END), 0) AS paid_avg_ticket
        FROM consumption_order co
    """)
    HashMap<String, Object> overview();

    @Select("""
        SELECT
          DATE_FORMAT(co.created_at, '%Y-%m-%d') AS biz_date,
          COUNT(*) AS total_orders,
          SUM(CASE WHEN co.status = 2 THEN 1 ELSE 0 END) AS paid_orders,
          COALESCE(SUM(co.amount), 0) AS gross_amount,
          COALESCE(SUM(CASE WHEN co.status = 2 THEN co.amount ELSE 0 END), 0) AS paid_amount
        FROM consumption_order co
        WHERE co.created_at >= DATE_SUB(CURDATE(), INTERVAL #{lookbackDays} DAY)
        GROUP BY DATE_FORMAT(co.created_at, '%Y-%m-%d')
        ORDER BY biz_date ASC
    """)
    List<HashMap<String, Object>> trend(@Param("lookbackDays") int lookbackDays);

    @Select("""
        SELECT
          pf.id AS platform_id,
          pf.name AS platform_name,
          COUNT(*) AS order_count,
          COALESCE(SUM(co.amount), 0) AS gross_amount,
          COALESCE(SUM(CASE WHEN co.status = 2 THEN co.amount ELSE 0 END), 0) AS paid_amount
        FROM consumption_order co
        JOIN product p ON co.product_id = p.id
        JOIN platform pf ON p.platform_id = pf.id
        GROUP BY pf.id, pf.name
        ORDER BY paid_amount DESC, order_count DESC
        LIMIT #{limit}
    """)
    List<HashMap<String, Object>> platformRank(@Param("limit") int limit);

    @Select("""
        SELECT
          pr.id AS province_id,
          pr.name AS province_name,
          COUNT(*) AS order_count,
          COALESCE(SUM(co.amount), 0) AS gross_amount,
          COALESCE(SUM(CASE WHEN co.status = 2 THEN co.amount ELSE 0 END), 0) AS paid_amount
        FROM consumption_order co
        JOIN city c ON co.city_id = c.id
        JOIN province pr ON c.province_id = pr.id
        GROUP BY pr.id, pr.name
        ORDER BY paid_amount DESC, order_count DESC
        LIMIT #{limit}
    """)
    List<HashMap<String, Object>> provinceRank(@Param("limit") int limit);

    @Select("""
        SELECT
          p.category,
          COUNT(*) AS order_count,
          COALESCE(SUM(co.amount), 0) AS gross_amount,
          COALESCE(SUM(CASE WHEN co.status = 2 THEN co.amount ELSE 0 END), 0) AS paid_amount
        FROM consumption_order co
        JOIN product p ON co.product_id = p.id
        GROUP BY p.category
        ORDER BY paid_amount DESC, order_count DESC
        LIMIT #{limit}
    """)
    List<HashMap<String, Object>> categoryRank(@Param("limit") int limit);

    @Select("""
        SELECT
          pay_channel,
          COUNT(*) AS payment_count,
          COALESCE(SUM(pay_amount), 0) AS paid_amount
        FROM payment_record
        WHERE pay_status = 1
        GROUP BY pay_channel
        ORDER BY paid_amount DESC, payment_count DESC
        LIMIT #{limit}
    """)
    List<HashMap<String, Object>> paymentChannelRank(@Param("limit") int limit);
}
