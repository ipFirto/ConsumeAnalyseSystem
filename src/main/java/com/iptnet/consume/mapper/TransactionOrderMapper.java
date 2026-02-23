package com.iptnet.consume.mapper;

import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

@Mapper
public interface TransactionOrderMapper {

    @Insert("""
        INSERT INTO cart_item (user_id, product_id, city_id, amount, quantity, status, created_at, updated_at)
        VALUES (#{userId}, #{productId}, #{cityId}, #{amount}, 1, 1, NOW(), NOW())
        ON DUPLICATE KEY UPDATE
            quantity = quantity + 1,
            amount = VALUES(amount),
            status = 1,
            updated_at = NOW()
    """)
    int cartItem(@Param("userId") Integer userId,
                 @Param("productId") Integer productId,
                 @Param("cityId") Integer cityId,
                 @Param("amount") BigDecimal amount);

    @Update("""
        UPDATE cart_item
        SET quantity = quantity - 1,
            updated_at = NOW()
        WHERE id = (
            SELECT id FROM (
                SELECT id
                FROM cart_item
                WHERE user_id = #{userId}
                  AND product_id = #{productId}
                  AND (#{cityId} IS NULL OR city_id = #{cityId})
                  AND quantity > 0
                  AND status = 1
                ORDER BY updated_at DESC
                LIMIT 1
            ) AS t
        )
    """)
    int declineCartItem(@Param("userId") Integer userId,
                        @Param("productId") Integer productId,
                        @Param("cityId") Integer cityId);

    @Delete("""
        DELETE FROM cart_item
        WHERE user_id = #{userId}
          AND product_id = #{productId}
          AND (#{cityId} IS NULL OR city_id = #{cityId})
          AND quantity <= 0
    """)
    int deleteCartItem(@Param("userId") Integer userId,
                       @Param("productId") Integer productId,
                       @Param("cityId") Integer cityId);

    @Select("""
        SELECT
          ci.id,
          ci.user_id,
          ci.product_id,
          ci.city_id,
          ci.quantity,
          p.brand,
          p.category,
          p.product_name,
          p.platform_id,
          p.status AS product_status,
          c.name AS city_name,
          ci.amount,
          ci.status AS cart_item_status,
          ci.created_at,
          ci.updated_at
        FROM cart_item ci
        JOIN product p ON p.id = ci.product_id
        JOIN city c ON c.id = ci.city_id
        WHERE ci.user_id = #{uid}
          AND ci.status = 1
          AND ci.quantity > 0
        ORDER BY ci.updated_at DESC, ci.id DESC
    """)
    List<HashMap<String, Object>> selectAllItem(@Param("uid") Integer uid);

    @Select("""
        SELECT
          co.id AS co_id,
          co.order_no AS co_order_no,
          co.user_id,
          co.product_id,
          co.city_id,
          co.quantity,
          c.name AS city_name,
          pv.name AS province_name,
          p.brand,
          p.product_name,
          p.category,
          pf.name AS platform_name,
          co.amount,
          co.status,
          co.remark AS co_remark,
          co.created_at AS co_created_at,
          co.updated_at AS co_updated_at,
          co.pay_deadline,
          co.pay_time,
          co.payment_method,
          co.payment_no
        FROM consumption_order co
        JOIN city c ON co.city_id = c.id
        JOIN province pv ON c.province_id = pv.id
        JOIN product p ON co.product_id = p.id
        JOIN platform pf ON p.platform_id = pf.id
        WHERE co.order_no = #{orderNo}
          AND co.user_id = #{uid}
        LIMIT 1
    """)
    HashMap<String, Object> selectOneOrder(@Param("uid") Integer uid,
                                           @Param("orderNo") String orderNo);

    @Select("""
        SELECT
          co.id AS co_id,
          co.order_no AS co_order_no,
          co.user_id,
          co.product_id,
          co.city_id,
          co.quantity,
          c.name AS city_name,
          pv.name AS province_name,
          p.brand,
          p.product_name,
          p.category,
          pf.name AS platform_name,
          co.amount,
          co.status,
          co.remark AS co_remark,
          co.created_at AS co_created_at,
          co.updated_at AS co_updated_at,
          co.pay_deadline,
          co.pay_time,
          co.payment_method,
          co.payment_no
        FROM consumption_order co
        JOIN city c ON co.city_id = c.id
        JOIN province pv ON c.province_id = pv.id
        JOIN product p ON co.product_id = p.id
        JOIN platform pf ON p.platform_id = pf.id
        WHERE co.user_id = #{uid}
        ORDER BY co.created_at DESC, co.id DESC LIMIT 30;
    """)
    List<HashMap<String, Object>> selectAllOrders(@Param("uid") Integer uid);

    @Select("""
        <script>
        SELECT
          co.id AS co_id,
          co.order_no AS co_order_no,
          co.user_id,
          co.product_id,
          co.city_id,
          co.quantity,
          c.name AS city_name,
          pv.name AS province_name,
          p.brand,
          p.product_name,
          p.category,
          pf.name AS platform_name,
          co.amount,
          co.status,
          co.remark AS co_remark,
          co.created_at AS co_created_at,
          co.updated_at AS co_updated_at,
          co.pay_deadline,
          co.pay_time,
          co.payment_method,
          co.payment_no
        FROM consumption_order co
        JOIN city c ON co.city_id = c.id
        JOIN province pv ON c.province_id = pv.id
        JOIN product p ON co.product_id = p.id
        JOIN platform pf ON p.platform_id = pf.id
        WHERE co.user_id = #{uid}
        <if test='status != null'>
          AND co.status = #{status}
        </if>
        ORDER BY co.created_at DESC, co.id DESC
        LIMIT #{limit}
        </script>
    """)
    List<HashMap<String, Object>> selectRecentOrders(@Param("uid") Integer uid,
                                                      @Param("limit") Integer limit,
                                                      @Param("status") Integer status);

    @Select("""
        <script>
        SELECT
          co.id AS co_id,
          co.order_no AS co_order_no,
          co.user_id,
          co.product_id,
          co.city_id,
          co.quantity,
          c.name AS city_name,
          pv.name AS province_name,
          p.brand,
          p.product_name,
          p.category,
          pf.name AS platform_name,
          co.amount,
          co.status,
          co.remark AS co_remark,
          co.created_at AS co_created_at,
          co.updated_at AS co_updated_at,
          co.pay_deadline,
          co.pay_time,
          co.payment_method,
          co.payment_no
        FROM consumption_order co
        JOIN city c ON co.city_id = c.id
        JOIN province pv ON c.province_id = pv.id
        JOIN product p ON co.product_id = p.id
        JOIN platform pf ON p.platform_id = pf.id
        WHERE co.user_id = #{uid}
        <if test='orderNos != null and orderNos.size > 0'>
          AND co.order_no IN
          <foreach collection='orderNos' item='orderNo' open='(' separator=',' close=')'>
            #{orderNo}
          </foreach>
        </if>
        ORDER BY co.created_at DESC, co.id DESC
        </script>
    """)
    List<HashMap<String, Object>> selectOrdersByNos(@Param("uid") Integer uid,
                                                     @Param("orderNos") List<String> orderNos);

    @Insert("""
        INSERT INTO consumption_order
          (user_id, order_no, product_id, city_id, quantity, amount, remark, status, pay_deadline, created_at, updated_at)
        VALUES
          (#{userId}, #{orderNo}, #{productId}, #{cityId}, #{quantity}, #{amount}, #{remark}, 1, #{payDeadline}, #{createdAt}, #{createdAt})
    """)
    int insertOrder(@Param("userId") Integer userId,
                    @Param("orderNo") String orderNo,
                    @Param("productId") Integer productId,
                    @Param("cityId") Integer cityId,
                    @Param("quantity") Integer quantity,
                    @Param("amount") BigDecimal amount,
                    @Param("remark") String remark,
                    @Param("createdAt") LocalDateTime createdAt,
                    @Param("payDeadline") LocalDateTime payDeadline);

    @Update("""
        UPDATE product
        SET stock = stock - #{quantity},
            updated_at = NOW()
        WHERE id = #{productId}
          AND status = 1
          AND stock >= #{quantity}
    """)
    int deductProductStock(@Param("productId") Integer productId,
                           @Param("quantity") Integer quantity);

    @Update("""
        UPDATE product
        SET stock = stock + #{quantity},
            updated_at = NOW()
        WHERE id = #{productId}
    """)
    int releaseProductStock(@Param("productId") Integer productId,
                            @Param("quantity") Integer quantity);

    @Update("""
        UPDATE consumption_order
        SET status = 2,
            pay_time = #{paidAt},
            payment_method = #{paymentMethod},
            payment_no = #{paymentNo},
            updated_at = #{paidAt},
            version = version + 1
        WHERE order_no = #{orderNo}
          AND user_id = #{uid}
          AND status = 1
          AND pay_deadline >= #{paidAt}
    """)
    int payIfUnpaid(@Param("uid") Integer uid,
                    @Param("orderNo") String orderNo,
                    @Param("paymentMethod") String paymentMethod,
                    @Param("paymentNo") String paymentNo,
                    @Param("paidAt") LocalDateTime paidAt);

    @Update("""
        UPDATE consumption_order
        SET status = 3,
            updated_at = NOW(),
            version = version + 1
        WHERE order_no = #{orderNo}
          AND status = 1
    """)
    int closeIfUnpaid(@Param("orderNo") String orderNo);

    @Update("""
        UPDATE consumption_order
        SET status = 4,
            updated_at = #{canceledAt},
            version = version + 1
        WHERE order_no = #{orderNo}
          AND user_id = #{uid}
          AND status = 1
    """)
    int cancelIfUnpaid(@Param("uid") Integer uid,
                       @Param("orderNo") String orderNo,
                       @Param("canceledAt") LocalDateTime canceledAt);

    @Update("""
        UPDATE consumption_order
        SET status = 3,
            updated_at = #{now},
            version = version + 1
        WHERE status = 1
          AND pay_deadline < #{now}
    """)
    int closeExpiredOrders(@Param("now") LocalDateTime now);

    @Select("""
        SELECT
          co.order_no,
          co.user_id,
          co.product_id,
          co.quantity
        FROM consumption_order co
        WHERE co.status = 1
          AND co.pay_deadline < #{now}
        ORDER BY co.pay_deadline ASC, co.id ASC
        LIMIT #{limit}
    """)
    List<HashMap<String, Object>> selectExpiredUnpaidOrders(@Param("now") LocalDateTime now,
                                                             @Param("limit") Integer limit);

    @Select("""
        SELECT
          co.order_no,
          co.user_id,
          co.product_id,
          co.quantity,
          co.amount,
          co.status,
          co.pay_deadline,
          co.pay_time,
          co.payment_method,
          co.payment_no
        FROM consumption_order co
        WHERE co.order_no = #{orderNo}
        LIMIT 1
    """)
    HashMap<String, Object> selectOrderMeta(@Param("orderNo") String orderNo);

    @Insert("""
        INSERT INTO payment_record
          (payment_no, order_no, user_id, pay_amount, pay_channel, pay_status, paid_at, request_payload, response_payload)
        VALUES
          (#{paymentNo}, #{orderNo}, #{uid}, #{payAmount}, #{payChannel}, #{payStatus}, #{paidAt}, #{requestPayload}, #{responsePayload})
    """)
    int insertPaymentRecord(@Param("paymentNo") String paymentNo,
                            @Param("orderNo") String orderNo,
                            @Param("uid") Integer uid,
                            @Param("payAmount") BigDecimal payAmount,
                            @Param("payChannel") String payChannel,
                            @Param("payStatus") Integer payStatus,
                            @Param("paidAt") LocalDateTime paidAt,
                            @Param("requestPayload") String requestPayload,
                            @Param("responsePayload") String responsePayload);

    @Insert("""
        INSERT INTO order_operate_log
          (order_no, user_id, operation, from_status, to_status, operator_type, operator_id, detail)
        VALUES
          (#{orderNo}, #{uid}, #{operation}, #{fromStatus}, #{toStatus}, #{operatorType}, #{operatorId}, #{detail})
    """)
    int insertOrderOperateLog(@Param("orderNo") String orderNo,
                              @Param("uid") Integer uid,
                              @Param("operation") String operation,
                              @Param("fromStatus") Integer fromStatus,
                              @Param("toStatus") Integer toStatus,
                              @Param("operatorType") String operatorType,
                              @Param("operatorId") String operatorId,
                              @Param("detail") String detail);
}
