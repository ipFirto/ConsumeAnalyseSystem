package com.iptnet.consume.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;


/**
 * @sqlParam   co_id 订单id   bigint
 * @sqlParam   co_order_no 订单号  varchar
 * @sqlParam   p_brand 品牌名  varchar
 * @sqlParam  product_name 产品名  varchar
 * @sqlParam   pf_name 平台名  varchar
 * @sqlParam   amount 金额    decimal(12,2)
 * @sqlParam   co_remark 备注 varchar
 * @sqlParam   co_created_at 订单创建时间
 * @sqlParam   c_id 城市id    bigint
 * @sqlParam   c_name 城市名   varchar
 * @sqlParam   c_code 城市编号  varchar
 * @sqlParam   pr_id 省id    bigint
 * @sqlParam   pr_name 省名   varchar
 * @sqlParam   pr_type 省类型  tinyint
 * @sqlParam   user_id 用户id int
 * @sqlParam   user_email 用户邮箱  varchar
 * @sqlParam   user_name 用户名    varchar
 * @sqlParam   u_phone 用户手机号    varchar
 * @sqlParam   u_status 用户禁用状态  boolean  0/1  禁用/启用
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsumeOrderDTO {

    /** co_id 订单id bigint */
    private Long coId;

    /** co_order_no 订单号 varchar */
    private String coOrderNo;

    /** p_brand 品牌名 varchar */
    private String pBrand;

    /** product_name 产品名 varchar */
    private String productName;

    /** pf_name 平台名 varchar */
    private String pfName;

    /** amount 金额 decimal(12,2) */
    private BigDecimal amount;

    /** co_remark 备注 varchar */
    private String coRemark;

    /** co_created_at 订单创建时间 */
    private LocalDateTime coCreatedAt;

    /** c_id 城市id bigint */
    private Long cId;

    /** c_name 城市名 varchar */
    private String cName;

    /** c_code 城市编号 varchar */
    private String cCode;

    /** pr_id 省id bigint */
    private Long prId;

    /** pr_name 省名 varchar */
    private String prName;

    /** pr_type 省类型 tinyint */
    private Integer prType;

    /** user_id 用户id int */
    private Integer userId;

    /** user_email 用户邮箱 varchar */
    private String userEmail;

    /** user_name 用户名 varchar */
    private String userName;

    /** u_phone 用户手机号 varchar */
    private String uPhone;

    /** u_status 用户禁用状态 boolean 0/1 */
    private Boolean uStatus;
}