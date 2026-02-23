package com.iptnet.consume.dto;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.data.annotation.Id;

@Data
public class UserInfo {

    @Id
    private String userId;

    @NotBlank
    @Email
    private String userEmail;

    @NotBlank
    private String userName;
}
