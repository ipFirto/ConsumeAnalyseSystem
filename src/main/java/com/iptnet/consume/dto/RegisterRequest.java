package com.iptnet.consume.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank
    @Email
    private String userEmail;

    private String userName;

    private String password;

    private String newPassword;

    private String validateCode; // 建议 String，避免丢前导 0
}

