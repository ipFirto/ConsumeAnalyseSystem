package com.iptnet.consume.dao;

import jakarta.validation.constraints.Email;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class User {

    private Integer userId;
    @Email
    private String userEmail;

    private String userName;

    private String passwordHash;

    private String phone;

    private LocalDateTime registerTime;

    private LocalDateTime lastLoginTime;

    private Integer status;
}
