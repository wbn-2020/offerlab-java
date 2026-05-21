package com.offerlab.community.user.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.user.application.UserApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@PublicApi
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserApplicationService userService;

    @PostMapping("/register")
    public Result<Map<String, Long>> register(@Valid @RequestBody RegisterReq req) {
        Long uid = userService.register(req.getEmail(), req.getPassword(), req.getNickname());
        return Result.ok(Map.of("uid", uid));
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginReq req, HttpServletRequest http) {
        String token = userService.login(req.getEmail(), req.getPassword(), http.getRemoteAddr());
        return Result.ok(Map.of("token", token));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String auth) {
        if (auth != null && auth.startsWith("Bearer ")) {
            userService.logout(auth.substring(7));
        }
        return Result.ok();
    }

    @Data
    public static class RegisterReq {
        @Email
        @NotBlank
        private String email;
        @NotBlank
        @Size(min = 6, max = 64)
        private String password;
        @NotBlank
        @Size(min = 2, max = 32)
        private String nickname;
    }

    @Data
    public static class LoginReq {
        @Email
        @NotBlank
        private String email;
        @NotBlank
        private String password;
    }
}
