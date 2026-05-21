package com.offerlab.community.infra.security;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminPermissionService {

    private final Set<Long> adminUids;
    private final AdminRoleMapper adminRoleMapper;

    public AdminPermissionService(@Value("${offerlab.admin.uid-whitelist:${OFFERLAB_ADMIN_UIDS:}}") String whitelist,
                                  AdminRoleMapper adminRoleMapper) {
        this.adminUids = parseWhitelist(whitelist);
        this.adminRoleMapper = adminRoleMapper;
    }

    public void requireAdmin(Long uid) {
        if (uid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (isAdmin(uid)) {
            return;
        }
        if (isLocalOpenMode()) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN);
    }

    public boolean isAdmin(Long uid) {
        if (uid == null) {
            return false;
        }
        if (adminUids.contains(uid)) {
            return true;
        }
        if (!adminTableExists()) {
            return false;
        }
        try {
            return adminRoleMapper.countActiveAdmin(uid) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public String mode() {
        if (adminTableExists() && countEnabledAdmins() > 0) {
            return "RBAC";
        }
        if (!adminUids.isEmpty()) {
            return "WHITELIST";
        }
        if (adminTableExists() && countAdminRows() > 0) {
            return "RBAC_EMPTY";
        }
        return "LOCAL_OPEN";
    }

    public boolean isLocalOpenMode() {
        return adminUids.isEmpty() && (!adminTableExists() || countAdminRows() == 0);
    }

    public boolean whitelistEnabled() {
        return !adminUids.isEmpty();
    }

    public boolean roleTableEnabled() {
        return adminTableExists() && countEnabledAdmins() > 0;
    }

    private boolean adminTableExists() {
        try {
            return adminRoleMapper.tableExists() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private int countEnabledAdmins() {
        try {
            return adminRoleMapper.countEnabledAdmins();
        } catch (Exception e) {
            return 0;
        }
    }

    private int countAdminRows() {
        try {
            return adminRoleMapper.countAdminRows();
        } catch (Exception e) {
            return 0;
        }
    }

    public void requireStrictAdmin(Long uid) {
        if (!isAdmin(uid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private static Set<Long> parseWhitelist(String whitelist) {
        if (!StringUtils.hasText(whitelist)) {
            return Set.of();
        }
        return Arrays.stream(whitelist.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(Long::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }
}
