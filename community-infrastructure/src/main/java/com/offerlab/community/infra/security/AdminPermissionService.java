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

    public AdminPermissionService(@Value("${offerlab.admin.uid-whitelist:${OFFERLAB_ADMIN_UIDS:}}") String whitelist) {
        this.adminUids = parseWhitelist(whitelist);
    }

    public void requireAdmin(Long uid) {
        if (uid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!adminUids.isEmpty() && !adminUids.contains(uid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    public boolean whitelistEnabled() {
        return !adminUids.isEmpty();
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
