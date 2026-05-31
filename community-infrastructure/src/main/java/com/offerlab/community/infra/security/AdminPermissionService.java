package com.offerlab.community.infra.security;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminPermissionService {
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_CONTENT_MODERATOR = "CONTENT_MODERATOR";
    public static final String ROLE_QUESTION_OPERATOR = "QUESTION_OPERATOR";
    public static final String ROLE_OPS = "OPS";

    private final Set<Long> adminUids;
    private final AdminRoleMapper adminRoleMapper;
    private final boolean localOpenEnabled;
    private final Environment environment;

    public AdminPermissionService(@Value("${offerlab.admin.uid-whitelist:${OFFERLAB_ADMIN_UIDS:}}") String whitelist,
                                  @Value("${offerlab.admin.local-open-enabled:${OFFERLAB_ADMIN_LOCAL_OPEN_ENABLED:false}}") boolean localOpenEnabled,
                                  AdminRoleMapper adminRoleMapper,
                                  Environment environment) {
        this.adminUids = parseWhitelist(whitelist);
        this.localOpenEnabled = localOpenEnabled;
        this.adminRoleMapper = adminRoleMapper;
        this.environment = environment;
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
        if (environment.matchesProfiles("prod")) {
            return "LOCKED";
        }
        return localOpenEnabled ? "LOCAL_OPEN" : "LOCKED";
    }

    public boolean isLocalOpenMode() {
        return localOpenEnabled
                && !environment.matchesProfiles("prod")
                && adminUids.isEmpty()
                && (!adminTableExists() || countAdminRows() == 0);
    }

    public boolean localOpenEnabled() {
        return localOpenEnabled;
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

    public void requireScope(Long uid, String roleCode) {
        if (uid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (isAdmin(uid) || hasRole(uid, roleCode)) {
            return;
        }
        if (isLocalOpenMode()) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN);
    }

    public boolean hasRole(Long uid, String roleCode) {
        if (uid == null || !StringUtils.hasText(roleCode)) {
            return false;
        }
        if (adminUids.contains(uid)) {
            return true;
        }
        if (!adminTableExists()) {
            return false;
        }
        try {
            return adminRoleMapper.countActiveRole(uid, roleCode.trim().toUpperCase()) > 0;
        } catch (Exception e) {
            return false;
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
