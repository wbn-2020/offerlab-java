package com.offerlab.community.analytics.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChannelQualityRiskGovernanceMilestoneQueryMapper {

    @Select("""
            SELECT m.id AS sourceFactId,
                   m.case_id AS caseId,
                   m.domain,
                   COALESCE(v40.case_version, c.case_version) AS caseVersion,
                   m.milestone_code AS factType,
                   m.occurred_at AS occurredAt,
                   m.owner_uid AS ownerUid,
                   m.responsibility_scope AS responsibilityScope,
                   m.responsibility_epoch AS responsibilityEpoch,
                   m.case_status_after AS caseStatusAfter,
                   m.required_action AS requiredAction,
                   m.retrospective_id AS retrospectiveId,
                   m.contract_version AS contractVersion
            FROM t_channel_quality_review_risk_case_governance_milestone m
            INNER JOIN t_channel_quality_review_risk_case c ON c.id = m.case_id
            LEFT JOIN t_channel_quality_review_risk_case_event v40
                   ON m.source_type = 'V40_CASE_EVENT' AND v40.id = m.source_id
            WHERE m.case_id = #{caseId}
              AND m.contract_version = 'V41_FACT_V1'
            ORDER BY m.id ASC
            """)
    List<ChannelQualityRiskGovernanceMilestoneFactRow> listFactsByCaseId(@Param("caseId") Long caseId);
}
