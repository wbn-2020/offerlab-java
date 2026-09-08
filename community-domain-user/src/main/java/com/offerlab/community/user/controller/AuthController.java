package com.offerlab.community.user.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.user.application.PasswordResetDeliveryPort;
import com.offerlab.community.user.application.PasswordResetService;
import com.offerlab.community.user.application.UserApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
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
    private final PasswordResetService passwordResetService;

    @PostMapping("/register")
    @RateLimit(key = "'auth:register:' + #http.remoteAddr", rate = 5, per = 3600, failOpen = false)
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterReq req, HttpServletRequest http) {
        Long uid = userService.register(
                req.getEmail(),
                req.getPassword(),
                req.getNickname(),
                req.isTermsAccepted(),
                req.isPrivacyAccepted(),
                req.getTermsVersion(),
                req.getPrivacyVersion());
        // 注册成功后直接签发会话，避免前端再发起一次登录并与新用户资料投影产生竞态。
        String token = userService.login(req.getEmail(), req.getPassword(), http.getRemoteAddr());
        return Result.ok(Map.of("uid", uid, "token", token));
    }

    @PostMapping("/login")
    @RateLimit(key = "'auth:login:' + #http.remoteAddr", rate = 20, per = 300, failOpen = false)
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginReq req, HttpServletRequest http) {
        String token = userService.login(req.accountValue(), req.getPassword(), http.getRemoteAddr());
        return Result.ok(Map.of("token", token));
    }

    /** 找回密码第一步：请求邮箱验证码。防枚举：邮箱不存在时也返回成功语义。 */
    @PostMapping("/password/reset-request")
    @RateLimit(key = "'auth:pwd-reset:' + #http.remoteAddr", rate = 5, per = 3600, failOpen = false)
    public Result<Map<String, Object>> requestPasswordReset(@Valid @RequestBody PasswordResetRequestReq req,
                                                            HttpServletRequest http) {
        String channel = passwordResetService.requestReset(req.getEmail());
        return Result.ok(Map.of(
                "delivered", !PasswordResetDeliveryPort.CHANNEL_SILENT.equals(channel),
                "channel", channel));
    }

    /** 找回密码第二步：验证码 + 新密码完成重置；成功后旧会话全部失效。 */
    @PostMapping("/password/reset-confirm")
    @RateLimit(key = "'auth:pwd-confirm:' + #http.remoteAddr", rate = 10, per = 3600, failOpen = false)
    public Result<Map<String, Object>> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmReq req,
                                                            HttpServletRequest http) {
        passwordResetService.confirmReset(req.getEmail(), req.getCode(), req.getNewPassword());
        return Result.ok(Map.of("reset", true));
    }

    @PostMapping("/logout")
    @RateLimit(key = "'auth:logout:' + #http.remoteAddr", rate = 60, per = 60, failOpen = false)
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String auth,
                               HttpServletRequest http) {
        if (auth != null && auth.startsWith("Bearer ")) {
            userService.logout(auth.substring(7));
        }
        return Result.ok();
    }

    @Data
    public static class RegisterReq {
        @Email(message = "请输入有效的邮箱地址")
        @NotBlank(message = "请输入邮箱")
        private String email;
        @NotBlank(message = "请输入密码")
        @Size(min = 8, max = 64, message = "密码长度需为 8-64 位")
        private String password;
        @NotBlank(message = "请输入昵称")
        @Size(min = 2, max = 32, message = "昵称长度需为 2-32 个字符")
        private String nickname;
        @AssertTrue(message = "请阅读并同意服务条款")
        private boolean termsAccepted;
        @AssertTrue(message = "请阅读并同意隐私政策")
        private boolean privacyAccepted;
        @NotBlank(message = "服务条款版本不能为空")
        @Size(max = 32, message = "服务条款版本格式不正确")
        private String termsVersion;
        @NotBlank(message = "隐私政策版本不能为空")
        @Size(max = 32, message = "隐私政策版本格式不正确")
        private String privacyVersion;
    }

    @Data
    public static class LoginReq {
        @Size(max = 128, message = "账号长度不能超过 128 个字符")
        private String account;
        @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
        private String email;
        @NotBlank(message = "请输入密码")
        @Size(max = 64, message = "密码长度不能超过 64 位")
        private String password;

        public String accountValue() {
            if (account != null && !account.isBlank()) {
                return account.trim();
            }
            return email == null ? "" : email.trim();
        }

        @AssertTrue(message = "请输入账号或邮箱")
        public boolean isAccountPresent() {
            return (account != null && !account.isBlank()) || (email != null && !email.isBlank());
        }
    }

    @Data
    public static class PasswordResetRequestReq {
        @Email(message = "请输入有效的邮箱地址")
        @NotBlank(message = "请输入注册时使用的邮箱")
        @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
        private String email;
    }

    @Data
    public static class PasswordResetConfirmReq {
        @Email(message = "请输入有效的邮箱地址")
        @NotBlank(message = "请输入注册时使用的邮箱")
        @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
        private String email;
        @NotBlank(message = "请输入邮箱验证码")
        @Size(min = 6, max = 6, message = "验证码为 6 位数字")
        private String code;
        @NotBlank(message = "请输入新密码")
        @Size(min = 8, max = 64, message = "密码长度需为 8-64 位")
        private String newPassword;
    }
}
