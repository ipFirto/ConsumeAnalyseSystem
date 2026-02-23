package com.iptnet.consume.controller;

import com.iptnet.consume.common.Result;
import com.iptnet.consume.dao.User;
import com.iptnet.consume.dto.RegisterRequest;
import com.iptnet.consume.dto.UserInfo;
import com.iptnet.consume.service.redis.RedisService;
import com.iptnet.consume.service.user.UserService;
import com.iptnet.consume.utils.CodeUtil;
import com.iptnet.consume.utils.EmailUtil;
import com.iptnet.consume.utils.JwtUtil;
import com.iptnet.consume.utils.Sha256Util;
import com.iptnet.consume.utils.ThreadLocalUtil;
import jakarta.validation.Valid;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user")
@Validated
public class UserController {

    private static final String MSG_EMAIL_REGISTERED = "邮箱已注册";
    private static final String MSG_CODE_EXPIRED = "验证码已过期";
    private static final String MSG_CODE_ERROR = "验证码错误";
    private static final String MSG_REGISTER_SUCCESS = "注册成功";
    private static final String MSG_USER_NOT_FOUND_REGISTER = "用户不存在，请注册";
    private static final String MSG_PASSWORD_ERROR = "密码输入错误！";
    private static final String MSG_NEW_PASSWORD_EMPTY = "新密码不能为空";
    private static final String MSG_USER_NOT_FOUND = "用户不存在";
    private static final String MSG_CODE_SENT = "验证码已发送";
    private static final String MSG_PASSWORD_UPDATED = "密码修改成功";

    @Autowired
    private UserService userService;

    @Autowired
    private RedisService redisService;

    @Autowired
    private EmailUtil emailUtil;

    @PostMapping("/register")
    public Result register(@Valid @RequestBody RegisterRequest req) {
        String email = req.getUserEmail();
        String username = req.getUserName();
        String redisCode = redisService.getCode(email);

        if (userService.findByEmail(email) != null) {
            System.out.println("user {" + email + "} register failed: email already exists");
            return Result.fail(MSG_EMAIL_REGISTERED);
        }
        if (redisCode == null) {
            return Result.fail(MSG_CODE_EXPIRED);
        }
        if (!redisCode.equals(req.getValidateCode())) {
            return Result.fail(MSG_CODE_ERROR);
        }

        userService.registerUser(email, username, Sha256Util.sha256(req.getPassword()));
        redisService.deleteCode(email);
        System.out.println("user {" + email + "} register success");
        return Result.success(MSG_REGISTER_SUCCESS);
    }

    @PostMapping("/login")
    public Result login(@Valid @RequestBody RegisterRequest req) {
        String email = req.getUserEmail();
        User loginUser = userService.loginUser(req);

        if (loginUser == null) {
            return Result.fail(MSG_USER_NOT_FOUND_REGISTER);
        }

        if (!Sha256Util.sha256(req.getPassword()).equals(loginUser.getPasswordHash())) {
            return Result.fail(MSG_PASSWORD_ERROR);
        }

        Map<String, Object> map = new HashMap<>();
        map.put("id", loginUser.getUserId());
        map.put("email", loginUser.getUserEmail());
        String jwtStr = JwtUtil.getToken(map);

        userService.updateLastLoginTime(loginUser.getUserId());
        redisService.deleteCode(email);
        System.out.println(LocalDateTime.now() + ": user {" + email + "} login success");
        return Result.success(jwtStr);
    }

    @GetMapping("/validateToken")
    public Result<Integer> validateToken() {
        return Result.success(200);
    }

    @PostMapping("/forget")
    public Result forget(@Valid @RequestBody RegisterRequest req) {
        String email = req.getUserEmail();
        String redisCode = redisService.getCode(email);

        if (redisCode == null) {
            return Result.fail(MSG_CODE_EXPIRED);
        }
        if (!redisCode.equals(req.getValidateCode())) {
            return Result.fail(MSG_CODE_ERROR);
        }

        User loginUser = userService.loginUser(req);
        if (loginUser == null) {
            return Result.fail(MSG_USER_NOT_FOUND_REGISTER);
        }

        String newPassword = req.getNewPassword();
        if (newPassword == null || newPassword.isBlank()) {
            return Result.fail(MSG_NEW_PASSWORD_EMPTY);
        }

        userService.updatePassword(Sha256Util.sha256(newPassword), loginUser.getUserId());
        redisService.deleteCode(email);
        return Result.success("user {" + email + "} --> " + MSG_PASSWORD_UPDATED);
    }

    @PostMapping("/info")
    public Result info() {
        Map<String, Object> map = ThreadLocalUtil.get();
        UserInfo userInfo = userService.findByEmail(map.get("email").toString());
        return Result.success(userInfo);
    }

    @PostMapping("/userInfo")
    public Result userInfo() {
        Map<String, Object> map = ThreadLocalUtil.get();
        User user = userService.findById((Integer) map.get("id"));
        return Result.success(user);
    }

    @PostMapping("/sendEmailCode")
    public Result sendEmailCode(@RequestParam String email) {
        String code = CodeUtil.generateCode();
        redisService.setCode(email, code);
        emailUtil.sendCode(email, code);
        System.out.println("email code sent for user {" + email + "}: " + code);
        return Result.success(MSG_CODE_SENT);
    }

    @PatchMapping("/updatePassword")
    public Result updateInfo(@Valid @RequestBody RegisterRequest req) {
        String redisCode = redisService.getCode(req.getUserEmail());
        if (redisCode == null) {
            return Result.fail(MSG_CODE_EXPIRED);
        }
        if (!redisCode.equals(req.getValidateCode())) {
            return Result.fail(MSG_CODE_ERROR);
        }

        String newPassword = req.getPassword();
        if (newPassword == null || newPassword.isBlank()) {
            return Result.fail(MSG_NEW_PASSWORD_EMPTY);
        }

        Map<String, Object> map = ThreadLocalUtil.get();
        Integer currentUserId = (Integer) map.get("id");
        User user = userService.findById(currentUserId);
        if (user == null) {
            return Result.fail(MSG_USER_NOT_FOUND);
        }

        userService.updatePassword(Sha256Util.sha256(newPassword), currentUserId);
        redisService.deleteCode(req.getUserEmail());
        return Result.success("user {" + user.getUserEmail() + "} --> " + MSG_PASSWORD_UPDATED);
    }

    @PostMapping("/deleteUser")
    public Result delete() {
        Map<String, Object> map = ThreadLocalUtil.get();
        userService.deleteUser((Integer) map.get("id"));
        return Result.success("success");
    }

    @PostMapping("/judgeIfExist")
    public Result judgeIfExist(@Param("email") String email) {
        return userService.judgeUserEmail(email) == null
                ? Result.success(null)
                : Result.fail(MSG_EMAIL_REGISTERED);
    }
}
