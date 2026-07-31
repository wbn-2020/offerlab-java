package com.offerlab.community.post.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.post.api.dto.DomainConfigDTO;
import com.offerlab.community.post.api.dto.DomainConfigUpdateCmd;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.domain.model.PostDomain;
import com.offerlab.community.post.infrastructure.persistence.mapper.DomainConfigMapper;
import com.offerlab.community.post.infrastructure.persistence.po.DomainConfigPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DomainConfigService {

    private static final String RISK_LOW = "LOW";
    private static final String RISK_MEDIUM = "MEDIUM";
    private static final String RISK_HIGH = "HIGH";

    private final DomainConfigMapper mapper;
    private final MigrationCheckService migrationCheckService;
    private final AdminPermissionService adminPermissionService;
    private final AdminAuditService adminAuditService;

    public List<DomainConfigDTO> listPublic() {
        if (!tableReady()) {
            return defaultConfigs().stream()
                    .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
                    .toList();
        }
        return mapper.selectPublicList().stream()
                .map(this::toDto)
                .toList();
    }

    public List<DomainConfigDTO> listAdmin() {
        return listAdmin(UserContext.require());
    }

    public List<DomainConfigDTO> listAdmin(Long operatorUid) {
        adminPermissionService.requireAdmin(operatorUid);
        if (!tableReady()) {
            return defaultConfigs();
        }
        return mapper.selectAdminList().stream()
                .map(this::toDto)
                .toList();
    }

    public DomainConfigDTO updateDomainConfig(Integer domain, DomainConfigUpdateCmd cmd, Long operatorUid) {
        adminPermissionService.requireAdmin(operatorUid);
        Integer activeDomain = requireKnownDomain(domain);
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        requireWritable();
        DomainConfigDTO before = getDomainConfig(activeDomain);
        int updated = mapper.updateDomainConfig(
                activeDomain,
                coalesce(clean(cmd.getDomainName(), 64), before.getDomainName()),
                coalesce(cleanSlug(cmd.getDomainSlug()), before.getDomainSlug()),
                coalesce(clean(cmd.getDescription(), 500), before.getDescription()),
                cmd.getSortOrder() == null ? before.getSortOrder() : Math.max(0, Math.min(cmd.getSortOrder(), 9999)),
                cmd.getEnabled() == null ? boolToInt(before.getEnabled()) : boolToInt(cmd.getEnabled()),
                cmd.getRiskLevel() == null ? before.getRiskLevel() : normalizeRiskLevel(cmd.getRiskLevel()),
                coalesce(clean(cmd.getPostingNotice(), 500), before.getPostingNotice()),
                coalesce(clean(cmd.getBrowseNotice(), 500), before.getBrowseNotice()),
                coalesce(clean(cmd.getInteractionNotice(), 500), before.getInteractionNotice()),
                operatorUid
        );
        if (updated <= 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        DomainConfigDTO after = getDomainConfig(activeDomain);
        adminAuditService.recordRequired(
                operatorUid,
                "DOMAIN_CONFIG_UPDATE",
                "DOMAIN_CONFIG",
                activeDomain,
                before,
                after,
                clean(cmd.getNote(), 500)
        );
        return after;
    }

    public void requireDomainEnabled(Integer domain) {
        DomainConfigDTO config = getDomainConfig(requireKnownDomain(domain));
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "当前领域暂未开放内容发布");
        }
    }

    public boolean reviewRequiredForPublish(Integer domain) {
        DomainConfigDTO config = getDomainConfig(requireKnownDomain(domain));
        return Objects.equals(domain, Post.DOMAIN_INVESTMENT)
                || RISK_HIGH.equalsIgnoreCase(config.getRiskLevel());
    }

    public String riskLevelForDomain(Integer domain) {
        return getDomainConfig(requireKnownDomain(domain)).getRiskLevel().toLowerCase(Locale.ROOT);
    }

    private DomainConfigDTO getDomainConfig(Integer domain) {
        if (!tableReady()) {
            return defaultByDomain().get(requireKnownDomain(domain));
        }
        DomainConfigPO po = mapper.selectByDomain(domain);
        if (po != null) {
            return toDto(po);
        }
        return defaultByDomain().get(requireKnownDomain(domain));
    }

    private List<DomainConfigDTO> defaultConfigs() {
        return List.of(
                defaultConfig(Post.DOMAIN_TECH, "tech", 10, RISK_LOW,
                        "技术实践、架构复盘、工程经验与工具资源。",
                        "请尽量补充背景、方案与结果，方便后来者复用。",
                        "公开技术内容默认可匿名浏览。",
                        "点赞、收藏、评论前请先登录。"),
                defaultConfig(Post.DOMAIN_CAREER, "career", 20, RISK_MEDIUM,
                        "求职复盘、成长路径、协作经验与职业选择。",
                        "请避免泄露敏感个人与企业信息。",
                        "部分内容可能匿名展示作者身份。",
                        "参与互动前请先登录并遵守社区礼仪。"),
                defaultConfig(Post.DOMAIN_READING, "reading", 30, RISK_LOW,
                        "书单、方法论、长文摘录与学习笔记。",
                        "鼓励给出你的理解、摘录边界与适用场景。",
                        "公开阅读内容可直接浏览。",
                        "登录后可收藏、评论与追踪后续讨论。"),
                defaultConfig(Post.DOMAIN_LIFESTYLE, "lifestyle", 40, RISK_LOW,
                        "生活效率、习惯、平衡与个人体验。",
                        "欢迎分享真实经验，避免医疗与法律断言。",
                        "公开生活内容可直接浏览。",
                        "互动前请先登录，保持友善表达。"),
                defaultConfig(Post.DOMAIN_INVESTMENT, "investment", 50, RISK_HIGH,
                        "理财认知、风险教育与个人复盘。",
                        "该领域内容默认进入更严格审核，请避免收益承诺与荐股表述。",
                        "浏览时请自行判断风险，社区不构成投资建议。",
                        "互动前请先登录并遵守风险提示。")
        );
    }

    private Map<Integer, DomainConfigDTO> defaultByDomain() {
        Map<Integer, DomainConfigDTO> result = new LinkedHashMap<>();
        for (DomainConfigDTO config : defaultConfigs()) {
            result.put(config.getDomain(), config);
        }
        return result;
    }

    private DomainConfigDTO defaultConfig(Integer domain, String slug, int sortOrder, String riskLevel,
                                          String description, String postingNotice,
                                          String browseNotice, String interactionNotice) {
        return DomainConfigDTO.builder()
                .domain(domain)
                .domainName(PostDomain.fromCode(domain).getDisplayName())
                .domainSlug(slug)
                .description(description)
                .sortOrder(sortOrder)
                .enabled(true)
                .riskLevel(riskLevel)
                .postingNotice(postingNotice)
                .browseNotice(browseNotice)
                .interactionNotice(interactionNotice)
                .createdBy(0L)
                .updatedBy(0L)
                .build();
    }

    private DomainConfigDTO toDto(DomainConfigPO po) {
        return DomainConfigDTO.builder()
                .domain(po.getDomain())
                .domainName(po.getDomainName())
                .domainSlug(po.getDomainSlug())
                .description(po.getDescription())
                .sortOrder(po.getSortOrder())
                .enabled(po.getEnabled() != null && po.getEnabled() == 1)
                .riskLevel(normalizeRiskLevel(po.getRiskLevel()))
                .postingNotice(po.getPostingNotice())
                .browseNotice(po.getBrowseNotice())
                .interactionNotice(po.getInteractionNotice())
                .createdBy(po.getCreatedBy())
                .updatedBy(po.getUpdatedBy())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    private void requireWritable() {
        if (!migrationCheckService.domainConfigReady()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR.getCode(),
                    "Domain config migration is required: db/migration/20260623_domain_config.sql");
        }
    }

    private boolean tableReady() {
        if (migrationCheckService.domainConfigReady()) {
            return true;
        }
        try {
            return mapper.tableExists() > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Integer requireKnownDomain(Integer domain) {
        if (PostDomain.isValid(domain)) {
            return domain;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private static String normalizeRiskLevel(String riskLevel) {
        if (!StringUtils.hasText(riskLevel)) {
            return RISK_LOW;
        }
        String normalized = riskLevel.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case RISK_LOW, RISK_MEDIUM, RISK_HIGH -> normalized;
            default -> throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "riskLevel is invalid");
        };
    }

    private static String clean(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private static String cleanSlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            return null;
        }
        String text = slug.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\-]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return text.isBlank() ? null : text.substring(0, Math.min(text.length(), 32));
    }

    private static Integer boolToInt(Boolean value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    private static <T> T coalesce(T current, T fallback) {
        return current != null ? current : fallback;
    }
}
