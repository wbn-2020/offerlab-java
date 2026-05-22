package com.offerlab.community.user.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerlab.community.user.domain.model.User;
import com.offerlab.community.user.domain.repository.UserRepository;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserAccountMapper;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserCounterMapper;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserProfileMapper;
import com.offerlab.community.user.infrastructure.persistence.po.UserAccountPO;
import com.offerlab.community.user.infrastructure.persistence.po.UserProfilePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserAccountMapper accountMapper;
    private final UserProfileMapper profileMapper;
    private final UserCounterMapper counterMapper;

    @Override
    @Transactional
    public User register(User user) {
        UserAccountPO acc = new UserAccountPO();
        acc.setId(user.getId());
        acc.setEmail(user.getEmail());
        acc.setPasswordHash(user.getPasswordHash());
        acc.setPasswordSalt("");
        acc.setAccountStatus(User.STATUS_NORMAL);
        accountMapper.insert(acc);

        UserProfilePO prof = new UserProfilePO();
        prof.setId(user.getId());
        prof.setNickname(user.getNickname());
        prof.setAvatarUrl(user.getAvatarUrl());
        prof.setBio(user.getBio());
        prof.setIntentJson(user.getIntentJson());
        profileMapper.insert(prof);

        counterMapper.initIfAbsent(user.getId());
        return user;
    }

    @Override
    public Optional<User> findById(Long id) {
        UserAccountPO acc = accountMapper.selectById(id);
        if (acc == null) return Optional.empty();
        UserProfilePO prof = profileMapper.selectById(id);
        return Optional.of(toDomain(acc, prof));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        UserAccountPO acc = accountMapper.selectOne(new LambdaQueryWrapper<UserAccountPO>()
                .eq(UserAccountPO::getEmail, email));
        if (acc == null) return Optional.empty();
        UserProfilePO prof = profileMapper.selectById(acc.getId());
        return Optional.of(toDomain(acc, prof));
    }

    @Override
    public void updateProfile(User user) {
        UserProfilePO prof = new UserProfilePO();
        prof.setId(user.getId());
        prof.setNickname(user.getNickname());
        prof.setAvatarUrl(user.getAvatarUrl());
        prof.setBio(user.getBio());
        prof.setIntentJson(user.getIntentJson());
        profileMapper.updateById(prof);
    }

    @Override
    public void updateLastLogin(Long uid, String ip) {
        UserAccountPO acc = new UserAccountPO();
        acc.setId(uid);
        acc.setLastLoginTime(LocalDateTime.now());
        acc.setLastLoginIp(ip);
        accountMapper.updateById(acc);
    }

    @Override
    public void updatePassword(Long uid, String passwordHash) {
        UserAccountPO acc = new UserAccountPO();
        acc.setId(uid);
        acc.setPasswordHash(passwordHash);
        accountMapper.updateById(acc);
    }

    private User toDomain(UserAccountPO acc, UserProfilePO prof) {
        User u = User.builder()
                .id(acc.getId())
                .email(acc.getEmail())
                .passwordHash(acc.getPasswordHash())
                .accountStatus(acc.getAccountStatus())
                .lastLoginTime(acc.getLastLoginTime())
                .createTime(acc.getCreateTime())
                .build();
        if (prof != null) {
            u.setNickname(prof.getNickname());
            u.setAvatarUrl(prof.getAvatarUrl());
            u.setBio(prof.getBio());
            u.setIntentJson(prof.getIntentJson());
        }
        return u;
    }
}
