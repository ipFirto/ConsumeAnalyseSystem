package com.iptnet.consume.mapper;

import com.iptnet.consume.dao.User;
import com.iptnet.consume.dto.RegisterRequest;
import com.iptnet.consume.dto.UserInfo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

@Mapper
public interface UserMapper {

    @Insert("INSERT INTO user (user_email, user_name, password_hash) VALUES (#{email},#{username},#{passwordHash})")
    void registerUser(String email, String username, String passwordHash);

    @Select("SELECT user_id,user_email,user_name,password_hash FROM user WHERE user_email = #{email} and status = 1 LIMIT 1;")
    User loginFindUser(String email);

    @Select("SELECT user_email,user_name FROM user WHERE user_email = #{email} and status = 1 LIMIT 1;")
    String judgeUserEmail(String email);

    @Select("select user_id,user_email, user_name from user WHERE user_email = #{email} and status = 1 LIMIT 1")
    UserInfo findUserByEmail(String email);

    @Select("select user_id, user_email, user_name, phone, register_time, last_login_time, status from user WHERE user_id = #{id} and status = 1 LIMIT 1")
    User findById(Integer id);

    @Update("update user set password_hash=#{newPassword} where user_id=#{id}")
    void updatePassword(String newPassword, Integer id);

    @Update("update user set last_login_time=now() where user_id=#{id}")
    void updateLastLoginTime(Integer id);

//    @Update("update user set user_email=#{email},user_name=#{username},phone=#{phone} where user_id=#{id}")
//    void updateUserInfo(String email, String username, String phone,Integer id);

    @Update("update user set status=0 where user_id=#{id}")
    void deleteUser(Integer id);

    @Select("SELECT user_id FROM `user` WHERE status = 1 ORDER BY user_id")
    List<Integer> listActiveUserIds();

//    用户id消费数据  (HashTable线程安全,且用于查询用户订单，一般用户订单量不会太大，所以查询效率不会太差)
    @Select("SELECT co.id AS co_id,co.order_no AS co_order_no ,p.brand AS p_brand,p.product_name as product_name,pf.`name` as pf_name,co.amount AS amount,co.remark AS co_remark,co.created_at AS co_created_at,c.id AS c_id,c.`name` as c_name,c.`code` AS c_code,pr.id AS pr_id,pr.`name` as pr_name,pr.type as pr_type,u.user_id AS user_id,u.user_email AS user_email,u.user_name AS user_name,u.phone AS u_phone,u.`status` AS u_status FROM consumption_order co JOIN product p ON p.id = co.product_id JOIN city c ON c.id = co.city_id JOIN province pr ON pr.id = c.province_id JOIN `user` u on u.user_id = co.user_id JOIN platform pf ON pf.id = p.platform_id WHERE u.user_id = #{user_id} ORDER BY co.id desc;")
    Hashtable<String,Object> consumeDataByUser();

}
