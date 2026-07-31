package com.offerlab.community.infra.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommunityRoleAccessService {

    private static final Set<String> ROLE_CODES = Set.of(
            "COLLABORATION_INITIATOR",
            "EXPANDED_CAMPAIGN_CONTRIBUTOR",
            "CAMPAIGN_CONTRIBUTOR",
            "TOPIC_CANDIDATE_RECOMMENDER",
            "CHANNEL_CURATOR",
            "CHANNEL_RESOURCE_MAINTAINER",
            "TOPIC_STEWARD",
            "LOW_RISK_CANDIDATE_SCOUT",
            "COMMUNITY_RULE_PROPOSER"
    );
    private static final Set<String> DOMAIN_CODES = Set.of(
            "TECH", "CAREER", "READING", "LIFESTYLE", "INVESTMENT"
    );

    private final CommunityRoleAccessMapper mapper;

    public boolean hasActiveGrant(Long uid, String roleCode, String domainCode) {
        if (uid == null || uid <= 0) {
            return false;
        }
        String role = normalize(roleCode);
        String domain = normalize(domainCode);
        if (!ROLE_CODES.contains(role) || !DOMAIN_CODES.contains(domain)) {
            return false;
        }
        try {
            return mapper.existingTableCount() == 3
                    && mapper.countActiveGrant(uid, role, domain) > 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }
}
