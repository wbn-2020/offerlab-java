package com.offerlab.community.infra.security;

import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

/**
 * 密码加密 / 校验，使用 BCrypt
 */
@Service
public class PasswordEncoder {

    public String encode(String raw) {
        return BCrypt.hashpw(raw, BCrypt.gensalt(10));
    }

    public boolean matches(String raw, String hashed) {
        try {
            return BCrypt.checkpw(raw, hashed);
        } catch (Exception e) {
            return false;
        }
    }
}
