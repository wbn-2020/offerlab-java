package com.offerlab.community.user.application;

/**
 * 验证码投递通道。生产环境配置 SMTP 后由邮件实现投递；
 * 未配置时由 OPS 通道实现兜底（验证码进入运维可查的受控通道），
 * 保证"找回密码"功能在无邮件基建的环境下依然端到端可用且可验收。
 */
public interface PasswordResetDeliveryPort {

    String CHANNEL_EMAIL = "email";
    String CHANNEL_OPS = "ops";
    String CHANNEL_SILENT = "silent";

    /**
     * 投递验证码。
     *
     * @return 实际使用的通道标识（email / ops），用于告知前端后续提示话术
     */
    String deliver(String normalizedEmail, Long uid, String code);
}
