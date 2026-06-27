package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.utils.RiskConfirmation;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.post.api.dto.DomainModeratorDTO;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.infrastructure.persistence.mapper.DomainModeratorMapper;
import com.offerlab.community.post.infrastructure.persistence.po.DomainModeratorPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DomainModeratorService {
    private static final int MAX_LIMIT = 200;

    private final DomainModeratorMapper mapper;
    private final AdminPermissionService adminPermissionService;
    private final AdminAuditService adminAuditService;
    private final SnowflakeIdGenerator idGenerator;
    private final MigrationCheckService migrationCheckService;

    public boolean canModerateDomain(Long uid, Integer domain) {
        if (uid == null) {
            return false;
        }
        if (adminPermissionService.isAdmin(uid)
                || adminPermissionService.hasRole(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR)
                || adminPermissionService.isLocalOpenMode()) {
            return true;
        }
        Integer normalizedDomain = requireKnownDomain(domain);
        if (!tableReady()) {
            return false;
        }
        try {
            return mapper.countActive(uid, normalizedDomain) > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void requireModerateDomain(Long uid, Integer domain) {
        if (uid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!canModerateDomain(uid, domain)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    public boolean isDomainModerator(Long uid) {
        return !listModeratedDomains(uid).isEmpty();
    }

    public List<Integer> listModeratedDomains(Long uid) {
        if (uid == null || adminPermissionService.isAdmin(uid)
                || adminPermissionService.hasRole(uid, AdminPermissionService.ROLE_CONTENT_MODERATOR)
                || adminPermissionService.isLocalOpenMode()
                || !tableReady()) {
            return List.of();
        }
        try {
            return mapper.selectEnabledByUid(uid).stream()
                    .map(DomainModeratorPO::getDomain)
                    .filter(DomainModeratorService::isKnownDomain)
                    .distinct()
                    .toList();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    public List<DomainModeratorDTO> listModerators(Integer domain, Boolean enabled, int limit) {
        requireTableReady();
        Integer normalizedDomain = domain == null ? null : requireKnownDomain(domain);
        Integer enabledFlag = enabled == null ? null : (enabled ? 1 : 0);
        return mapper.selectByDomain(normalizedDomain, enabledFlag, safeLimit(limit)).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public DomainModeratorDTO upsertModerator(Long targetUid, Integer domain, Long operatorUid, String note) {
        requireTableReady();
        adminPermissionService.requireAdmin(operatorUid);
        Long uid = requireUid(targetUid);
        Integer normalizedDomain = requireKnownDomain(domain);
        String auditNote = RiskConfirmation.requireHigh(note);
        mapper.upsertModerator(idGenerator.nextId(), uid, normalizedDomain, operatorUid);
        DomainModeratorDTO dto = findOne(uid, normalizedDomain);
        adminAuditService.recordRequired(operatorUid, "DOMAIN_MODERATOR_UPSERT", "DOMAIN_MODERATOR",
                uid + ":" + normalizedDomain, null,
                Map.of("uid", uid, "domain", normalizedDomain, "enabled", true), auditNote);
        return dto;
    }

    @Transactional
    public DomainModeratorDTO updateModeratorStatus(Long targetUid, Integer domain, Boolean enabled, Long operatorUid, String note) {
        requireTableReady();
        adminPermissionService.requireAdmin(operatorUid);
        Long uid = requireUid(targetUid);
        Integer normalizedDomain = requireKnownDomain(domain);
        if (enabled == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        String auditNote = RiskConfirmation.requireHigh(note);
        DomainModeratorDTO before = findOne(uid, normalizedDomain);
        int updated = mapper.updateModeratorStatus(uid, normalizedDomain, enabled ? 1 : 0);
        if (updated <= 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        DomainModeratorDTO after = findOne(uid, normalizedDomain);
        adminAuditService.recordRequired(operatorUid,
                enabled ? "DOMAIN_MODERATOR_ENABLE" : "DOMAIN_MODERATOR_DISABLE",
                "DOMAIN_MODERATOR", uid + ":" + normalizedDomain,
                before, after, auditNote);
        return after;
    }

    private DomainModeratorDTO findOne(Long uid, Integer domain) {
        return mapper.selectByDomain(domain, null, MAX_LIMIT).stream()
                .filter(item -> uid.equals(item.getUid()))
                .findFirst()
                .map(this::toDto)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private DomainModeratorDTO toDto(DomainModeratorPO po) {
        return DomainModeratorDTO.builder()
                .id(po.getId())
                .uid(po.getUid())
                .domain(po.getDomain())
                .enabled(po.getEnabled() != null && po.getEnabled() == 1)
                .createdBy(po.getCreatedBy())
                .createdAt(po.getCreateTime())
                .updatedAt(po.getUpdateTime())
                .build();
    }

    private void requireTableReady() {
        if (!tableReady()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "Domain moderator migration is required: db/migration/20260617_domain_moderators.sql");
        }
    }

    private boolean tableReady() {
        try {
            return migrationCheckService.domainModeratorReady();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(limit <= 0 ? 50 : limit, MAX_LIMIT));
    }

    private static Long requireUid(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return uid;
    }

    private static Integer requireKnownDomain(Integer domain) {
        if (isKnownDomain(domain)) {
            return domain;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private static boolean isKnownDomain(Integer domain) {
        return domain != null && (domain == Post.DOMAIN_TECH
                || domain == Post.DOMAIN_CAREER
                || domain == Post.DOMAIN_READING
                || domain == Post.DOMAIN_LIFESTYLE
                || domain == Post.DOMAIN_INVESTMENT);
    }
}
