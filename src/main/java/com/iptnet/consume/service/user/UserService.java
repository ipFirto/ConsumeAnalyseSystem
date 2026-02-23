package com.iptnet.consume.service.user;

import com.iptnet.consume.dao.User;
import com.iptnet.consume.dto.RegisterRequest;
import com.iptnet.consume.dto.UserInfo;

public interface UserService {

    void registerUser(String email, String username, String passwordHash);
    User loginUser(RegisterRequest registerRequest);
    UserInfo findByEmail(String email);
    void updatePassword(String newPassword,Integer id);
    void updateLastLoginTime(Integer id);
    User findById(Integer id);
    void deleteUser(Integer id);
    String judgeUserEmail(String email);
}
