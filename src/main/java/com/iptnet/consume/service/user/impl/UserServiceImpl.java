package com.iptnet.consume.service.user.impl;

import com.iptnet.consume.dao.User;
import com.iptnet.consume.dto.RegisterRequest;
import com.iptnet.consume.dto.UserInfo;
import com.iptnet.consume.mapper.UserMapper;
import com.iptnet.consume.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public void registerUser(String email, String username, String passwordHash) {
        userMapper.registerUser(email,username,passwordHash);
    }

    @Override
    public User loginUser(RegisterRequest registerRequest) {
        return userMapper.loginFindUser(registerRequest.getUserEmail());
    }

    @Override
    public UserInfo findByEmail(String email) {
        return userMapper.findUserByEmail(email);
    }

    @Override
    public void updatePassword(String newPassword,Integer id) {
        userMapper.updatePassword(newPassword,id);
    }

    @Override
    public void updateLastLoginTime(Integer id) {
        userMapper.updateLastLoginTime(id);
    }

    @Override
    public User findById(Integer id) {
        return userMapper.findById(id);
    }

    @Override
    public void deleteUser(Integer id) {
        userMapper.deleteUser(id);
    }

    @Override
    public String judgeUserEmail(String email) {
        return userMapper.judgeUserEmail(email);
    }
}
