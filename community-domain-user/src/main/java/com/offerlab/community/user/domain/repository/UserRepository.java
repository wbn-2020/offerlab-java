package com.offerlab.community.user.domain.repository;

import com.offerlab.community.user.domain.model.User;

import java.util.Optional;

public interface UserRepository {

    /** 注册：插入账号和资料，返回带 id 的用户 */
    User register(User user);

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    void updateProfile(User user);

    void updateLastLogin(Long uid, String ip);
}
