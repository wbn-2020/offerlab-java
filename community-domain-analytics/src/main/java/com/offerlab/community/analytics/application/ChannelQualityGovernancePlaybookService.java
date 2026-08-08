package com.offerlab.community.analytics.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.offerlab.community.analytics.api.ChannelQualityGovernancePlaybookInputQueryFacade;
import com.offerlab.community.analytics.api.ChannelQualityRiskCasePlaybookInputQueryFacade;
import com.offerlab.community.analytics.api.ChannelQualityRiskGovernanceSnapshotQueryFacade;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernancePlaybookCommands.BindCasePlaybookCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernancePlaybookCommands.CasePlaybookTransitionCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernancePlaybookCommands.CreatePlaybookCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernancePlaybookCommands.CreateVersionCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernancePlaybookCommands.VerifyCheckCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernancePlaybookCommands.VersionTransitionCmd;
import com.offerlab.community.analytics.api.dto.ChannelQualityGovernancePlaybookDTO;
import com.offerlab.community.analytics.infrastructure.persistence.ChannelQualityGovernancePlaybookMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.audit.AdminAuditService;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.post.application.DomainModeratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChannelQualityGovernancePlaybookService
        implements ChannelQualityGovernancePlaybookInputQueryFacade, ChannelQualityRiskCasePlaybookInputQueryFacade {

    private static final String V41_SCHEMA = "V41_GOVERNANCE_SNAPSHOT_V1";
    private static final String SNAPSHOT_SCHEMA = "V44_CASE_PLAYBOOK_SNAPSHOT_V1";
    private static final Set<String> CASE_TERMINAL = Set.of("CLOSED");
    private static final Set<String> BINDABLE = Set.of("MATCHED", "PARTIAL");
    private static final Set<String> REQUIRED_FACTS = Set.of(
            "ROOT_CAUSE", "OUTCOME", "CONTENT_RECOVERY_STATE", "LEARNING_CATEGORY",
            "RECURRENCE_LINK", "EVIDENCE", "ACTION_REFERENCE", "RESOLUTION_SUBMITTED_AT");

    private final ChannelQualityGovernancePlaybookMapper mapper;
    private final ChannelQualityPlaybookContentCodec contentCodec;
    private final ChannelQualityRiskGovernanceSnapshotQueryFacade governanceSnapshotFacade;
    private final SnowflakeIdGenerator idGenerator;
    private final AdminPermissionService adminPermissionService;
    private final DomainModeratorService domainModeratorService;
    private final AdminAuditService adminAuditService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ChannelQualityGovernancePlaybookDTO getPlaybook(Long playbookId, Long operatorUid) {
        requireTables();
        requireOperator(operatorUid);
        ChannelQualityGovernancePlaybookDTO playbook = requirePlaybook(mapper.selectPlaybook(requireId(playbookId)));
        boolean manager = canManageLibrary(operatorUid);
        if (!manager) {
            requireReadablePublished(playbook, operatorUid);
        }
        playbook.setVersions(mapper.listVersions(playbook.getId()).stream()
                .filter(version -> manager || "PUBLISHED".equals(version.getStatus()))
                .map(this::safeVersion)
                .toList());
        applyLibraryCapabilities(playbook, manager);
        return playbook;
    }

    @Transactional(readOnly = true)
    public List<ChannelQualityGovernancePlaybookDTO> listPlaybooks(String status, Integer domain, Long operatorUid) {
        requireTables();
        requireOperator(operatorUid);
        String requestedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (requestedStatus != null && !Set.of("DRAFT", "PUBLISHED", "RETIRED").contains(requestedStatus)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (domain != null && (domain < 1 || domain > 5)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        boolean manager = canManageLibrary(operatorUid);
        return mapper.listPlaybooks().stream()
                .map(playbook -> {
                    List<ChannelQualityGovernancePlaybookDTO.VersionDTO> versions = mapper.listVersions(playbook.getId()).stream()
                            .filter(version -> manager || "PUBLISHED".equals(version.getStatus()))
                            .map(this::safeVersion)
                            .filter(version -> requestedStatus == null || requestedStatus.equals(version.getStatus()))
                            .filter(version -> domain == null || scopeAllowsDomain(version, domain))
                            .toList();
                    if (versions.isEmpty()) {
                        return null;
                    }
                    playbook.setVersions(versions);
                    applyLibraryCapabilities(playbook, manager);
                    return playbook;
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChannelQualityGovernancePlaybookDTO.VersionDTO getVersion(Long versionId, Long operatorUid) {
        requireTables();
        requireOperator(operatorUid);
        ChannelQualityGovernancePlaybookDTO.VersionDTO version = requireVersion(mapper.selectVersion(requireId(versionId)));
        if (!canManageLibrary(operatorUid) && !"PUBLISHED".equals(version.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return safeVersion(version);
    }

    @Transactional
    public ChannelQualityGovernancePlaybookDTO create(CreatePlaybookCmd cmd, Long operatorUid) {
        requireTables();
        requireLibraryManager(operatorUid);
        CreatePlaybookCmd request = requireCreate(cmd);
        String fingerprint = fingerprint("CREATE_PLAYBOOK", request.getPlaybookCode(), request.getReason(),
                contentCodec.encode(request.getContent()).contentHash());
        ChannelQualityGovernancePlaybookDTO replayed = replay(operatorUid, request.getIdempotencyKey(),
                fingerprint, ChannelQualityGovernancePlaybookDTO.class);
        if (replayed != null) {
            return replayed;
        }
        adminAuditService.requireWritable("V44_PLAYBOOK_CREATE", "CHANNEL_QUALITY_PLAYBOOK", null);
        ChannelQualityPlaybookContentCodec.EncodedContent encoded = contentCodec.encode(request.getContent());
        long playbookId = idGenerator.nextId();
        long versionId = idGenerator.nextId();
        requireWrite(mapper.insertPlaybook(playbookId, request.getPlaybookCode().trim().toUpperCase(Locale.ROOT)));
        requireWrite(mapper.insertDraftVersion(versionId, playbookId, 1, encoded.schemaVersion(), encoded.summary(),
                encoded.canonicalJson(), encoded.contentHash(), operatorUid));
        insertPlaybookEvent(playbookId, null, "PLAYBOOK_CREATED", operatorUid, request.getReason());
        insertPlaybookEvent(playbookId, versionId, "VERSION_DRAFTED", operatorUid, request.getReason());
        ChannelQualityGovernancePlaybookDTO result = requirePlaybook(mapper.selectPlaybook(playbookId));
        result.setVersions(List.of(safeVersion(requireVersion(mapper.selectVersion(versionId)))));
        applyLibraryCapabilities(result, true);
        audit(operatorUid, "V44_PLAYBOOK_CREATE", playbookId, Map.of(
                "playbookId", playbookId, "versionId", versionId, "contentHash", encoded.contentHash()), request.getReason());
        recordCommand(operatorUid, request.getIdempotencyKey(), fingerprint, result);
        return result;
    }

    @Transactional
    public ChannelQualityGovernancePlaybookDTO.VersionDTO createDraft(
            Long playbookId, CreateVersionCmd cmd, Long operatorUid) {
        requireTables();
        requireLibraryManager(operatorUid);
        CreateVersionCmd request = requireCreateVersion(cmd);
        ChannelQualityPlaybookContentCodec.EncodedContent encoded = contentCodec.encode(request.getContent());
        String fingerprint = fingerprint("CREATE_DRAFT", requireId(playbookId), request.getExpectedPlaybookVersion(),
                request.getBaseVersionId(), request.getReason(), encoded.contentHash());
        ChannelQualityGovernancePlaybookDTO.VersionDTO replayed = replay(operatorUid, request.getIdempotencyKey(),
                fingerprint, ChannelQualityGovernancePlaybookDTO.VersionDTO.class);
        if (replayed != null) {
            return replayed;
        }
        adminAuditService.requireWritable("V44_PLAYBOOK_VERSION_DRAFT", "CHANNEL_QUALITY_PLAYBOOK", playbookId);
        ChannelQualityGovernancePlaybookDTO locked = requirePlaybook(mapper.lockPlaybook(requireId(playbookId)));
        requirePlaybookVersion(locked, request.getExpectedPlaybookVersion());
        if (mapper.listVersions(locked.getId()).stream().anyMatch(version -> "DRAFT".equals(version.getStatus()))) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        if (request.getBaseVersionId() != null) {
            ChannelQualityGovernancePlaybookDTO.VersionDTO base = requireVersion(mapper.selectVersion(request.getBaseVersionId()));
            if (!locked.getId().equals(base.getPlaybookId())) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
        }
        int nextVersionNo = locked.getLatestVersionNo() + 1;
        long versionId = idGenerator.nextId();
        requireWrite(mapper.insertDraftVersion(versionId, locked.getId(), nextVersionNo, encoded.schemaVersion(),
                encoded.summary(), encoded.canonicalJson(), encoded.contentHash(), operatorUid));
        requireWrite(mapper.advancePlaybookVersion(locked.getId(), locked.getPlaybookVersion(), nextVersionNo));
        insertPlaybookEvent(locked.getId(), versionId, "VERSION_DRAFTED", operatorUid, request.getReason());
        ChannelQualityGovernancePlaybookDTO.VersionDTO result = safeVersion(requireVersion(mapper.selectVersion(versionId)));
        audit(operatorUid, "V44_PLAYBOOK_VERSION_DRAFT", locked.getId(), Map.of(
                "versionId", versionId, "contentHash", encoded.contentHash()), request.getReason());
        recordCommand(operatorUid, request.getIdempotencyKey(), fingerprint, result);
        return result;
    }

    @Transactional
    public ChannelQualityGovernancePlaybookDTO.VersionDTO publish(
            Long versionId, VersionTransitionCmd cmd, Long operatorUid) {
        return transitionVersion(versionId, cmd, operatorUid, "PUBLISH");
    }

    @Transactional
    public ChannelQualityGovernancePlaybookDTO.VersionDTO retire(
            Long versionId, VersionTransitionCmd cmd, Long operatorUid) {
        return transitionVersion(versionId, cmd, operatorUid, "RETIRE");
    }

    @Transactional(readOnly = true)
    public ChannelQualityGovernancePlaybookDTO.RecommendationPageDTO recommendations(
            Long caseId, Integer size, Long operatorUid) {
        requireTables();
        ChannelQualityRiskGovernanceSnapshotQueryFacade.V41GovernanceSnapshot snapshot =
                stableSnapshot(caseId, operatorUid, false);
        int limit = size == null ? 10 : Math.max(1, Math.min(size, 10));
        List<ChannelQualityGovernancePlaybookDTO.RecommendationDTO> candidates = new ArrayList<>();
        for (ChannelQualityGovernancePlaybookDTO.VersionDTO version : mapper.listPublishedVersions()) {
            ChannelQualityGovernancePlaybookDTO.VersionDTO safe = safeVersion(version);
            Applicability applicability = applicability(safe, snapshot);
            if (!"NOT_MATCHED".equals(applicability.result())) {
                candidates.add(ChannelQualityGovernancePlaybookDTO.RecommendationDTO.builder()
                        .playbookId(safe.getPlaybookId())
                        .playbookVersionId(safe.getId())
                        .versionNo(safe.getVersionNo())
                        .contentSummary(safe.getContentSummary())
                        .contentHash(safe.getContentHash())
                        .applicabilityResult(applicability.result())
                        .reasonCodes(applicability.reasonCodes())
                        .build());
            }
        }
        candidates.sort((left, right) -> {
            int result = rankWeight(right.getApplicabilityResult()) - rankWeight(left.getApplicabilityResult());
            return result != 0 ? result : Long.compare(left.getPlaybookVersionId(), right.getPlaybookVersionId());
        });
        for (int index = 0; index < candidates.size(); index++) {
            candidates.get(index).setRank(index + 1);
        }
        return ChannelQualityGovernancePlaybookDTO.RecommendationPageDTO.builder()
                .caseId(snapshot.caseId())
                .caseVersion(snapshot.caseVersion())
                .governanceFactVersion(snapshot.governanceFactVersion())
                .governanceSnapshotEtag(snapshot.governanceSnapshotEtag())
                .trendContextState("UNAVAILABLE")
                .items(candidates.stream().limit(limit).toList())
                .build();
    }

    @Transactional
    public ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO bind(
            Long caseId, BindCasePlaybookCmd cmd, Long operatorUid) {
        requireTables();
        BindCasePlaybookCmd request = requireBind(cmd);
        String fingerprint = fingerprint("BIND", requireId(caseId), request.getPlaybookVersionId(),
                request.getExpectedCaseVersion(), request.getExpectedGovernanceFactVersion(),
                request.getExpectedGovernanceSnapshotEtag(), request.getExpectedContentHash(), request.getNote());
        ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO replayed = replay(operatorUid, request.getIdempotencyKey(),
                fingerprint, ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO.class);
        if (replayed != null) {
            return replayed;
        }
        ChannelQualityRiskGovernanceSnapshotQueryFacade.V41GovernanceSnapshot snapshot =
                stableSnapshot(caseId, operatorUid, true);
        requireCaseVersions(snapshot, request.getExpectedCaseVersion(), request.getExpectedGovernanceFactVersion(),
                request.getExpectedGovernanceSnapshotEtag());
        adminAuditService.requireWritable("V44_CASE_PLAYBOOK_BIND", "CHANNEL_QUALITY_RISK_CASE", caseId);
        ChannelQualityGovernancePlaybookDTO.VersionDTO version = requireVersion(
                mapper.lockVersion(request.getPlaybookVersionId()));
        if (!"PUBLISHED".equals(version.getStatus()) || !version.getContentHash().equals(request.getExpectedContentHash())) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        Applicability applicability = applicability(safeVersion(version), snapshot);
        if (!BINDABLE.contains(applicability.result())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        ObjectNode snapshotJson = JsonNodeFactory.instance.objectNode();
        snapshotJson.put("schemaVersion", SNAPSHOT_SCHEMA);
        snapshotJson.put("caseId", snapshot.caseId());
        snapshotJson.put("playbookId", version.getPlaybookId());
        snapshotJson.put("playbookVersionId", version.getId());
        snapshotJson.put("sourceContentHash", version.getContentHash());
        snapshotJson.set("content", readJson(version.getCanonicalContentJson()));
        snapshotJson.put("applicabilityResult", applicability.result());
        ArrayNode reasons = snapshotJson.putArray("reasonCodes");
        applicability.reasonCodes().forEach(reasons::add);
        snapshotJson.put("observedCaseVersion", snapshot.caseVersion());
        snapshotJson.put("observedGovernanceFactVersion", snapshot.governanceFactVersion());
        snapshotJson.put("observedGovernanceSnapshotEtag", snapshot.governanceSnapshotEtag());
        long bindingId = idGenerator.nextId();
        String snapshotText = json(snapshotJson);
        requireWrite(mapper.insertCasePlaybook(bindingId, snapshot.caseId(), version.getPlaybookId(), version.getId(),
                version.getContentHash(), version.getContentSummary(), snapshotText, contentCodec.snapshotHash(snapshotJson),
                applicability.result(), json(objectMapper.valueToTree(applicability.reasonCodes())),
                snapshot.caseVersion(), snapshot.governanceFactVersion(), snapshot.governanceSnapshotEtag()));
        insertCaseEvent(bindingId, snapshot.caseId(), "BOUND", null, "PROPOSED", operatorUid,
                request.getIdempotencyKey(), request.getNote());
        ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO result = decorateCasePlaybook(
                requireCasePlaybook(mapper.selectCasePlaybook(bindingId)), operatorUid, snapshot);
        audit(operatorUid, "V44_CASE_PLAYBOOK_BIND", bindingId, Map.of(
                "caseId", snapshot.caseId(), "contentHash", version.getContentHash(),
                "snapshotHash", result.getSnapshotHash()), request.getNote());
        recordCommand(operatorUid, request.getIdempotencyKey(), fingerprint, result);
        return result;
    }

    @Transactional
    public ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO accept(
            Long casePlaybookId, CasePlaybookTransitionCmd cmd, Long operatorUid) {
        return transitionCasePlaybook(casePlaybookId, cmd, operatorUid, "ACCEPT", null);
    }

    @Transactional
    public ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO verifyCheck(
            Long casePlaybookId, String checkKey, VerifyCheckCmd cmd, Long operatorUid) {
        requireTables();
        VerifyCheckCmd request = requireVerify(cmd);
        String fingerprint = fingerprint("VERIFY_CHECK", requireId(casePlaybookId), requireText(checkKey, 2, 80),
                request.getExpectedBindingVersion(), request.getExpectedCheckVersion(),
                request.getExpectedGovernanceSnapshotEtag(), request.getEvidenceIds(), request.getNote());
        ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO replayed = replay(operatorUid, request.getIdempotencyKey(),
                fingerprint, ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO.class);
        if (replayed != null) {
            return replayed;
        }
        ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO binding = requireCasePlaybook(
                mapper.lockCasePlaybook(casePlaybookId));
        ChannelQualityRiskGovernanceSnapshotQueryFacade.V41GovernanceSnapshot snapshot =
                stableSnapshot(binding.getCaseId(), operatorUid, true);
        requireCaseTransitionVersions(binding, snapshot, request);
        if (!"ACCEPTED".equals(binding.getBindingStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        ChannelQualityGovernancePlaybookDTO.CheckDTO check = mapper.lockCheck(binding.getId(), checkKey);
        if (check == null || !"PENDING".equals(check.getState())
                || !request.getExpectedCheckVersion().equals(check.getCheckVersion())) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        List<Long> evidenceIds = requireEvidence(check, request.getEvidenceIds(), snapshot);
        adminAuditService.requireWritable("V44_CASE_PLAYBOOK_CHECK_PASS", "CHANNEL_QUALITY_RISK_CASE_PLAYBOOK", binding.getId());
        requireWrite(mapper.transitionCheck(check.getId(), "PASSED", snapshot.governanceFactVersion(),
                snapshot.governanceSnapshotEtag(), operatorUid, request.getNote(), null, check.getCheckVersion()));
        for (Long evidenceId : evidenceIds) {
            requireWrite(mapper.insertCheckEvidence(check.getId(), evidenceId, operatorUid));
        }
        insertCaseEvent(binding.getId(), binding.getCaseId(), "CHECK_PASSED", "ACCEPTED", "ACCEPTED",
                operatorUid, request.getIdempotencyKey(), request.getNote());
        ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO result = decorateCasePlaybook(
                requireCasePlaybook(mapper.selectCasePlaybook(binding.getId())), operatorUid, snapshot);
        audit(operatorUid, "V44_CASE_PLAYBOOK_CHECK_PASS", check.getId(), Map.of(
                "casePlaybookId", binding.getId(), "checkKey", checkKey), request.getNote());
        recordCommand(operatorUid, request.getIdempotencyKey(), fingerprint, result);
        return result;
    }

    @Transactional
    public ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO waiveCheck(
            Long casePlaybookId, String checkKey, VerifyCheckCmd cmd, Long operatorUid) {
        requireTables();
        VerifyCheckCmd request = requireVerify(cmd);
        String fingerprint = fingerprint("WAIVE_CHECK", requireId(casePlaybookId), requireText(checkKey, 2, 80),
                request.getExpectedBindingVersion(), request.getExpectedCheckVersion(),
                request.getExpectedGovernanceSnapshotEtag(), request.getNote());
        ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO replayed = replay(operatorUid, request.getIdempotencyKey(),
                fingerprint, ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO.class);
        if (replayed != null) {
            return replayed;
        }
        ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO binding = requireCasePlaybook(
                mapper.lockCasePlaybook(casePlaybookId));
        ChannelQualityRiskGovernanceSnapshotQueryFacade.V41GovernanceSnapshot snapshot =
                stableSnapshot(binding.getCaseId(), operatorUid, true);
        requireCaseTransitionVersions(binding, snapshot, request);
        if (!"ACCEPTED".equals(binding.getBindingStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        ChannelQualityGovernancePlaybookDTO.CheckDTO check = mapper.lockCheck(binding.getId(), checkKey);
        if (check == null || !"PENDING".equals(check.getState())
                || !request.getExpectedCheckVersion().equals(check.getCheckVersion())) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        adminAuditService.requireWritable("V44_CASE_PLAYBOOK_CHECK_WAIVE", "CHANNEL_QUALITY_RISK_CASE_PLAYBOOK", binding.getId());
        requireWrite(mapper.transitionCheck(check.getId(), "WAIVED", snapshot.governanceFactVersion(),
                snapshot.governanceSnapshotEtag(), operatorUid, null, request.getNote(), check.getCheckVersion()));
        insertCaseEvent(binding.getId(), binding.getCaseId(), "CHECK_WAIVED", "ACCEPTED", "ACCEPTED",
                operatorUid, request.getIdempotencyKey(), request.getNote());
        ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO result = decorateCasePlaybook(
                requireCasePlaybook(mapper.selectCasePlaybook(binding.getId())), operatorUid, snapshot);
        audit(operatorUid, "V44_CASE_PLAYBOOK_CHECK_WAIVE", check.getId(), Map.of(
                "casePlaybookId", binding.getId(), "checkKey", checkKey), request.getNote());
        recordCommand(operatorUid, request.getIdempotencyKey(), fingerprint, result);
        return result;
    }

    @Transactional
    public ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO complete(
            Long casePlaybookId, CasePlaybookTransitionCmd cmd, Long operatorUid) {
        return transitionCasePlaybook(casePlaybookId, cmd, operatorUid, "COMPLETE", null);
    }

    @Transactional
    public ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO waive(
            Long casePlaybookId, CasePlaybookTransitionCmd cmd, Long operatorUid) {
        return transitionCasePlaybook(casePlaybookId, cmd, operatorUid, "WAIVE", null);
    }

    @Transactional(readOnly = true)
    public List<ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO> casePlaybooks(Long caseId, Long operatorUid) {
        requireTables();
        ChannelQualityRiskGovernanceSnapshotQueryFacade.V41GovernanceSnapshot snapshot =
                stableSnapshot(caseId, operatorUid, false);
        return mapper.listCasePlaybooks(snapshot.caseId()).stream()
                .map(binding -> decorateCasePlaybook(requireCasePlaybook(binding), operatorUid, snapshot))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PlaybookInput getPublishedPlaybook(Long playbookVersionId) {
        requireTables();
        ChannelQualityGovernancePlaybookDTO.VersionDTO version = safeVersion(
                requireVersion(mapper.selectVersion(requireId(playbookVersionId))));
        if (!"PUBLISHED".equals(version.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        return new PlaybookInput(PlaybookInput.SCHEMA_VERSION, version.getPlaybookId(), version.getId(),
                version.getVersionNo(), version.getContentSummary(), version.getCanonicalContentJson(), version.getContentHash());
    }

    @Override
    @Transactional(readOnly = true)
    public CasePlaybookInput getCasePlaybook(Long casePlaybookId) {
        requireTables();
        ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO binding = requireCasePlaybook(
                mapper.selectCasePlaybook(requireId(casePlaybookId)));
        verifyCaseSnapshot(binding);
        if (!Set.of("ACCEPTED", "COMPLETED", "WAIVED").contains(binding.getBindingStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        return new CasePlaybookInput(CasePlaybookInput.SCHEMA_VERSION, binding.getId(), binding.getCaseId(),
                binding.getPlaybookId(), binding.getPlaybookVersionId(), binding.getBindingStatus(),
                binding.getSourceContentHash(), binding.getSnapshotContentJson(), binding.getSnapshotHash(),
                binding.getApplicabilityResult(), binding.getCompletionEvaluationHash());
    }

    private ChannelQualityGovernancePlaybookDTO.VersionDTO transitionVersion(
            Long versionId, VersionTransitionCmd cmd, Long operatorUid, String action) {
        requireTables();
        requireLibraryManager(operatorUid);
        VersionTransitionCmd request = requireVersionTransition(cmd);
        String fingerprint = fingerprint(action, requireId(versionId), request.getExpectedPlaybookVersion(),
                request.getExpectedContentHash(), request.getReason());
        ChannelQualityGovernancePlaybookDTO.VersionDTO replayed = replay(operatorUid, request.getIdempotencyKey(),
                fingerprint, ChannelQualityGovernancePlaybookDTO.VersionDTO.class);
        if (replayed != null) {
            return replayed;
        }
        ChannelQualityGovernancePlaybookDTO.VersionDTO lockedVersion = requireVersion(mapper.lockVersion(versionId));
        ChannelQualityGovernancePlaybookDTO playbook = requirePlaybook(mapper.lockPlaybook(lockedVersion.getPlaybookId()));
        requirePlaybookVersion(playbook, request.getExpectedPlaybookVersion());
        if (!request.getExpectedContentHash().equals(lockedVersion.getContentHash())) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        contentCodec.verify(lockedVersion.getCanonicalContentJson(), lockedVersion.getContentHash());
        String auditAction = "PUBLISH".equals(action) ? "V44_PLAYBOOK_VERSION_PUBLISH" : "V44_PLAYBOOK_VERSION_RETIRE";
        adminAuditService.requireWritable(auditAction, "CHANNEL_QUALITY_PLAYBOOK", playbook.getId());
        if ("PUBLISH".equals(action)) {
            if (!"DRAFT".equals(lockedVersion.getStatus())) {
                throw new BizException(ErrorCode.INVALID_STATUS);
            }
            requireWrite(mapper.retirePriorPublished(playbook.getId(), lockedVersion.getId()));
            requireWrite(mapper.publishVersion(lockedVersion.getId(), lockedVersion.getContentHash()));
            insertPlaybookEvent(playbook.getId(), lockedVersion.getId(), "VERSION_PUBLISHED", operatorUid, request.getReason());
        } else {
            if (!Set.of("DRAFT", "PUBLISHED").contains(lockedVersion.getStatus())) {
                throw new BizException(ErrorCode.INVALID_STATUS);
            }
            requireWrite(mapper.retireVersion(lockedVersion.getId(), lockedVersion.getContentHash()));
            insertPlaybookEvent(playbook.getId(), lockedVersion.getId(), "VERSION_RETIRED", operatorUid, request.getReason());
        }
        requireWrite(mapper.advancePlaybookVersion(playbook.getId(), playbook.getPlaybookVersion(), playbook.getLatestVersionNo()));
        ChannelQualityGovernancePlaybookDTO.VersionDTO result = safeVersion(requireVersion(mapper.selectVersion(versionId)));
        audit(operatorUid, auditAction, playbook.getId(), Map.of(
                "versionId", result.getId(), "contentHash", result.getContentHash(), "status", result.getStatus()),
                request.getReason());
        recordCommand(operatorUid, request.getIdempotencyKey(), fingerprint, result);
        return result;
    }

    private ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO transitionCasePlaybook(
            Long casePlaybookId, CasePlaybookTransitionCmd cmd, Long operatorUid, String action, String ignored) {
        requireTables();
        CasePlaybookTransitionCmd request = requireCaseTransition(cmd);
        String fingerprint = fingerprint(action, requireId(casePlaybookId), request.getExpectedBindingVersion(),
                request.getExpectedCaseVersion(), request.getExpectedGovernanceFactVersion(),
                request.getExpectedGovernanceSnapshotEtag(), request.getNote());
        ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO replayed = replay(operatorUid, request.getIdempotencyKey(),
                fingerprint, ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO.class);
        if (replayed != null) {
            return replayed;
        }
        ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO binding = requireCasePlaybook(
                mapper.lockCasePlaybook(casePlaybookId));
        ChannelQualityRiskGovernanceSnapshotQueryFacade.V41GovernanceSnapshot snapshot =
                stableSnapshot(binding.getCaseId(), operatorUid, true);
        requireCaseTransitionVersions(binding, snapshot, request);
        String target;
        String expected;
        String event;
        String evaluationHash = null;
        if ("ACCEPT".equals(action)) {
            target = "ACCEPTED";
            expected = "PROPOSED";
            event = "ACCEPTED";
            acceptChecks(binding);
        } else if ("COMPLETE".equals(action)) {
            target = "COMPLETED";
            expected = "ACCEPTED";
            event = "COMPLETED";
            evaluationHash = completionEvaluationHash(binding, snapshot);
        } else {
            target = "WAIVED";
            expected = binding.getBindingStatus();
            if (!Set.of("PROPOSED", "ACCEPTED").contains(expected)) {
                throw new BizException(ErrorCode.INVALID_STATUS);
            }
            event = "WAIVED";
        }
        if (!expected.equals(binding.getBindingStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        String auditAction = "V44_CASE_PLAYBOOK_" + action;
        adminAuditService.requireWritable(auditAction, "CHANNEL_QUALITY_RISK_CASE_PLAYBOOK", binding.getId());
        requireWrite(mapper.transitionCasePlaybook(binding.getId(), expected, target, binding.getBindingVersion(), evaluationHash));
        insertCaseEvent(binding.getId(), binding.getCaseId(), event, expected, target, operatorUid,
                request.getIdempotencyKey(), request.getNote());
        ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO result = decorateCasePlaybook(
                requireCasePlaybook(mapper.selectCasePlaybook(binding.getId())), operatorUid, snapshot);
        audit(operatorUid, auditAction, binding.getId(), Map.of(
                "caseId", binding.getCaseId(), "status", result.getBindingStatus(),
                "evaluationHash", evaluationHash == null ? "" : evaluationHash), request.getNote());
        recordCommand(operatorUid, request.getIdempotencyKey(), fingerprint, result);
        return result;
    }

    private void acceptChecks(ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO binding) {
        if (!mapper.listChecks(binding.getId()).isEmpty()) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        JsonNode checks = readJson(binding.getSnapshotContentJson()).path("content").path("requiredChecks");
        if (!checks.isArray()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        for (JsonNode check : checks) {
            requireWrite(mapper.insertCheck(idGenerator.nextId(), binding.getId(),
                    requiredJsonText(check, "checkKey"), requiredJsonText(check, "title"),
                    requiredJsonText(check, "instruction"), requiredJsonText(check, "evidenceRequirement")));
        }
    }

    private String completionEvaluationHash(
            ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO binding,
            ChannelQualityRiskGovernanceSnapshotQueryFacade.V41GovernanceSnapshot snapshot) {
        List<ChannelQualityGovernancePlaybookDTO.CheckDTO> checks = mapper.listChecks(binding.getId());
        if (checks.stream().anyMatch(check -> !Set.of("PASSED", "WAIVED").contains(check.getState())
                || !snapshot.governanceFactVersion().equals(check.getObservedFactVersion())
                || !snapshot.governanceSnapshotEtag().equals(check.getObservedSnapshotEtag()))) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        JsonNode conditions = readJson(binding.getSnapshotContentJson()).path("content").path("closeConditions");
        if (!conditions.isArray()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        List<String> failures = new ArrayList<>();
        for (JsonNode condition : conditions) {
            if ("BLOCKING".equals(requiredJsonText(condition, "severity"))
                    && !conditionSatisfied(requiredJsonText(condition, "conditionType"), binding, snapshot, checks)) {
                failures.add(requiredJsonText(condition, "conditionKey"));
            }
        }
        if (!failures.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        ObjectNode evaluation = JsonNodeFactory.instance.objectNode();
        evaluation.put("casePlaybookId", binding.getId());
        evaluation.put("caseVersion", snapshot.caseVersion());
        evaluation.put("governanceFactVersion", snapshot.governanceFactVersion());
        evaluation.put("governanceSnapshotEtag", snapshot.governanceSnapshotEtag());
        ArrayNode states = evaluation.putArray("checks");
        checks.forEach(check -> states.add(check.getCheckKey() + ":" + check.getState() + ":" + check.getCheckVersion()));
        return contentCodec.snapshotHash(evaluation);
    }

    private boolean conditionSatisfied(String type, ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO binding,
                                       ChannelQualityRiskGovernanceSnapshotQueryFacade.V41GovernanceSnapshot snapshot,
                                       List<ChannelQualityGovernancePlaybookDTO.CheckDTO> checks) {
        return switch (type) {
            case "ALL_REQUIRED_CHECKS_TERMINAL" -> checks.stream()
                    .allMatch(check -> Set.of("PASSED", "WAIVED").contains(check.getState()));
            case "V41_ROOT_CAUSE_PRESENT" -> !snapshot.rootCauseCategories().isEmpty();
            case "V41_OUTCOME_PRESENT" -> StringUtils.hasText(snapshot.outcomeType());
            case "V41_LEARNING_CATEGORY_PRESENT" -> !snapshot.learningCategories().isEmpty();
            case "V41_EVIDENCE_TYPE_PRESENT" -> !snapshot.evidenceRefs().isEmpty();
            case "V41_ACTION_REFERENCE_PRESENT" -> !snapshot.actionRefs().isEmpty();
            case "NO_ACCEPTED_PLAYBOOK_IN_PROGRESS" -> mapper.listCasePlaybooks(binding.getCaseId()).stream()
                    .noneMatch(item -> !binding.getId().equals(item.getId()) && "ACCEPTED".equals(item.getBindingStatus()));
            default -> throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        };
    }

    private Applicability applicability(ChannelQualityGovernancePlaybookDTO.VersionDTO version,
                                        ChannelQualityRiskGovernanceSnapshotQueryFacade.V41GovernanceSnapshot snapshot) {
        JsonNode scope = readJson(version.getCanonicalContentJson()).path("scope");
        if (!scope.isObject()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        List<String> reasons = new ArrayList<>();
        if (!requiredFactsPresent(scope.path("requiredFacts"), snapshot, reasons)) {
            return new Applicability("INSUFFICIENT_CONTEXT", List.copyOf(reasons));
        }
        if (!scope.path("triggerTypes").isArray()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if (!scope.path("triggerTypes").isEmpty()) {
            reasons.add("MISSING_TRIGGER_TYPE");
            return new Applicability("INSUFFICIENT_CONTEXT", List.copyOf(reasons));
        }
        if (!scope.path("riskCodes").isArray()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if (!scope.path("riskCodes").isEmpty()) {
            reasons.add("MISSING_RISK_CODE");
            return new Applicability("INSUFFICIENT_CONTEXT", List.copyOf(reasons));
        }
        boolean matches = scalarMatches(scope.path("domains"), snapshot.domain(), "DOMAIN", reasons)
                && scalarMatches(scope.path("rootCauseCategories"), snapshot.rootCauseCategories(), "ROOT_CAUSE", reasons)
                && scalarMatches(scope.path("outcomeTypes"), snapshot.outcomeType(), "OUTCOME", reasons)
                && scalarMatches(scope.path("contentRecoveryStates"), snapshot.contentRecoveryState(), "RECOVERY", reasons)
                && scalarMatches(scope.path("learningCategories"), snapshot.learningCategories(), "LEARNING", reasons)
                && scalarMatches(scope.path("recurrenceRelationTypes"),
                    snapshot.recurrenceLinks().stream().map(ChannelQualityRiskGovernanceSnapshotQueryFacade.RecurrenceLink::relationType).toList(),
                    "RECURRENCE", reasons);
        return new Applicability(matches ? "MATCHED" : "NOT_MATCHED", List.copyOf(reasons));
    }

    private boolean requiredFactsPresent(JsonNode fields,
                                        ChannelQualityRiskGovernanceSnapshotQueryFacade.V41GovernanceSnapshot snapshot,
                                        List<String> reasons) {
        if (!fields.isArray()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        for (JsonNode value : fields) {
            String field = value.asText();
            if (!REQUIRED_FACTS.contains(field)) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
            boolean available = switch (field) {
                case "ROOT_CAUSE" -> !snapshot.rootCauseCategories().isEmpty();
                case "OUTCOME" -> StringUtils.hasText(snapshot.outcomeType());
                case "CONTENT_RECOVERY_STATE" -> StringUtils.hasText(snapshot.contentRecoveryState());
                case "LEARNING_CATEGORY" -> !snapshot.learningCategories().isEmpty();
                case "RECURRENCE_LINK" -> !snapshot.recurrenceLinks().isEmpty();
                case "EVIDENCE" -> !snapshot.evidenceRefs().isEmpty();
                case "ACTION_REFERENCE" -> !snapshot.actionRefs().isEmpty();
                case "RESOLUTION_SUBMITTED_AT" -> available(snapshot.resolutionSubmittedAt());
                default -> false;
            };
            if (!available) {
                reasons.add("MISSING_" + field);
            }
        }
        return reasons.isEmpty();
    }

    private static boolean scalarMatches(JsonNode expected, Object actual, String label, List<String> reasons) {
        if (!expected.isArray()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if (expected.isEmpty()) {
            reasons.add(label + "_UNRESTRICTED");
            return true;
        }
        Set<String> desired = new HashSet<>();
        expected.forEach(value -> desired.add(value.asText()));
        if (actual instanceof List<?> actualList) {
            boolean matched = actualList.stream().map(String::valueOf).anyMatch(desired::contains);
            reasons.add(label + (matched ? "_MATCHED" : "_NOT_MATCHED"));
            return matched;
        }
        boolean matched = actual != null && desired.contains(String.valueOf(actual));
        reasons.add(label + (matched ? "_MATCHED" : "_NOT_MATCHED"));
        return matched;
    }

    private ChannelQualityRiskGovernanceSnapshotQueryFacade.V41GovernanceSnapshot stableSnapshot(
            Long caseId, Long operatorUid, boolean writable) {
        requireOperator(operatorUid);
        ChannelQualityRiskGovernanceSnapshotQueryFacade.V41GovernanceSnapshot snapshot;
        try {
            snapshot = governanceSnapshotFacade.getSnapshot(requireId(caseId));
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if (snapshot == null || !V41_SCHEMA.equals(snapshot.schemaVersion()) || snapshot.caseId() == null
                || snapshot.domain() == null || snapshot.domain() < 1 || snapshot.domain() > 5
                || snapshot.caseVersion() == null || snapshot.caseVersion() < 0
                || snapshot.governanceFactVersion() == null || snapshot.governanceFactVersion() < 0
                || !isHash(snapshot.governanceSnapshotEtag())) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        if (!domainModeratorService.canModerateDomain(operatorUid, snapshot.domain())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (writable && CASE_TERMINAL.contains(snapshot.caseStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        return snapshot;
    }

    private List<Long> requireEvidence(ChannelQualityGovernancePlaybookDTO.CheckDTO check, List<Long> evidenceIds,
                                        ChannelQualityRiskGovernanceSnapshotQueryFacade.V41GovernanceSnapshot snapshot) {
        if (!"REFERENCE_REQUIRED".equals(check.getEvidenceRequirement())) {
            return List.of();
        }
        if (evidenceIds == null || evidenceIds.isEmpty() || evidenceIds.size() > 20) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Set<Long> allowed = snapshot.evidenceRefs().stream()
                .map(ChannelQualityRiskGovernanceSnapshotQueryFacade.EvidenceRef::evidenceId)
                .collect(java.util.stream.Collectors.toSet());
        if (evidenceIds.stream().anyMatch(id -> id == null || id <= 0 || !allowed.contains(id))
                || evidenceIds.size() != new HashSet<>(evidenceIds).size()) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        return List.copyOf(evidenceIds);
    }

    private ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO decorateCasePlaybook(
            ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO binding, Long operatorUid,
            ChannelQualityRiskGovernanceSnapshotQueryFacade.V41GovernanceSnapshot snapshot) {
        verifyCaseSnapshot(binding);
        binding.setChecks(mapper.listChecks(binding.getId()));
        boolean moderate = domainModeratorService.canModerateDomain(operatorUid, snapshot.domain());
        boolean accepted = "ACCEPTED".equals(binding.getBindingStatus());
        binding.setCanAccept(moderate && "PROPOSED".equals(binding.getBindingStatus()));
        binding.setCanVerifyCheck(moderate && accepted);
        binding.setCanWaiveCheck(moderate && accepted);
        binding.setCanComplete(moderate && accepted);
        binding.setCanWaive(moderate && Set.of("PROPOSED", "ACCEPTED").contains(binding.getBindingStatus()));
        return binding;
    }

    private void verifyCaseSnapshot(ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO binding) {
        if (!isHash(binding.getSourceContentHash()) || !isHash(binding.getSnapshotHash())
                || !isHash(binding.getObservedGovernanceSnapshotEtag())
                || !Set.of("PROPOSED", "ACCEPTED", "COMPLETED", "WAIVED").contains(binding.getBindingStatus())) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        JsonNode snapshot = readJson(binding.getSnapshotContentJson());
        if (!SNAPSHOT_SCHEMA.equals(snapshot.path("schemaVersion").asText())
                || snapshot.path("caseId").asLong() != binding.getCaseId()
                || snapshot.path("playbookVersionId").asLong() != binding.getPlaybookVersionId()
                || !binding.getSourceContentHash().equals(snapshot.path("sourceContentHash").asText())
                || !binding.getSnapshotHash().equals(contentCodec.snapshotHash(snapshot))) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private void requireCaseTransitionVersions(ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO binding,
                                               ChannelQualityRiskGovernanceSnapshotQueryFacade.V41GovernanceSnapshot snapshot,
                                               CasePlaybookTransitionCmd cmd) {
        if (!cmd.getExpectedBindingVersion().equals(binding.getBindingVersion())) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        requireCaseVersions(snapshot, cmd.getExpectedCaseVersion(), cmd.getExpectedGovernanceFactVersion(),
                cmd.getExpectedGovernanceSnapshotEtag());
    }

    private static void requireCaseVersions(ChannelQualityRiskGovernanceSnapshotQueryFacade.V41GovernanceSnapshot snapshot,
                                            Integer caseVersion, Integer factVersion, String etag) {
        if (!caseVersion.equals(snapshot.caseVersion()) || !factVersion.equals(snapshot.governanceFactVersion())
                || !etag.equals(snapshot.governanceSnapshotEtag())) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    private void requireReadablePublished(ChannelQualityGovernancePlaybookDTO playbook, Long operatorUid) {
        boolean readable = mapper.listVersions(playbook.getId()).stream()
                .filter(version -> "PUBLISHED".equals(version.getStatus()))
                .map(this::safeVersion)
                .map(version -> readJson(version.getCanonicalContentJson()).path("scope").path("domains"))
                .flatMap(node -> {
                    List<Integer> domains = new ArrayList<>();
                    node.forEach(value -> domains.add(value.asInt()));
                    return domains.stream();
                })
                .anyMatch(domain -> domainModeratorService.canModerateDomain(operatorUid, domain));
        if (!readable) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private boolean scopeAllowsDomain(ChannelQualityGovernancePlaybookDTO.VersionDTO version, Integer domain) {
        JsonNode domains = readJson(version.getCanonicalContentJson()).path("scope").path("domains");
        if (!domains.isArray()) {
            return false;
        }
        if (domains.isEmpty()) {
            return true;
        }
        for (JsonNode item : domains) {
            if (item.asInt() == domain) {
                return true;
            }
        }
        return false;
    }

    private ChannelQualityGovernancePlaybookDTO.VersionDTO safeVersion(
            ChannelQualityGovernancePlaybookDTO.VersionDTO version) {
        contentCodec.verify(version.getCanonicalContentJson(), version.getContentHash());
        return version;
    }

    private void applyLibraryCapabilities(ChannelQualityGovernancePlaybookDTO playbook, boolean manager) {
        playbook.setCanCreateVersion(manager);
        playbook.setCanPublish(manager);
        playbook.setCanRetire(manager);
    }

    private boolean canManageLibrary(Long uid) {
        return uid != null && (adminPermissionService.isAdmin(uid)
                || adminPermissionService.hasRole(uid, AdminPermissionService.ROLE_OPS));
    }

    private void requireLibraryManager(Long uid) {
        requireOperator(uid);
        if (!canManageLibrary(uid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private void insertPlaybookEvent(Long playbookId, Long versionId, String type, Long uid, String reason) {
        requireWrite(mapper.insertPlaybookEvent(idGenerator.nextId(), playbookId, versionId, type, uid, reason));
    }

    private void insertCaseEvent(Long bindingId, Long caseId, String type, String previousStatus, String status,
                                 Long uid, String idempotencyKey, String reason) {
        requireWrite(mapper.insertCaseEvent(idGenerator.nextId(), bindingId, caseId, type, previousStatus, status,
                uid, idempotencyKey, reason));
    }

    private <T> T replay(Long uid, String idempotencyKey, String fingerprint, Class<T> type) {
        Map<String, Object> stored = mapper.selectCommand(uid, idempotencyKey);
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        Object savedFingerprint = stored.get("requestFingerprint");
        if (!fingerprint.equals(savedFingerprint)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Object raw = stored.get("resultJson");
        if (!(raw instanceof String resultJson)) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
        try {
            return objectMapper.readValue(resultJson, type);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private void recordCommand(Long uid, String idempotencyKey, String fingerprint, Object result) {
        try {
            requireWrite(mapper.insertCommand(uid, idempotencyKey, fingerprint, json(result)));
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    private void audit(Long uid, String action, Long resourceId, Map<String, Object> after, String remark) {
        adminAuditService.recordRequired(uid, action, "CHANNEL_QUALITY_PLAYBOOK", resourceId, null, after, remark);
    }

    private void requireTables() {
        try {
            if (mapper.tableCount() != 5) {
                throw new BizException(ErrorCode.DEPENDENCY_ERROR);
            }
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private static CreatePlaybookCmd requireCreate(CreatePlaybookCmd cmd) {
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        cmd.setPlaybookCode(requireText(cmd.getPlaybookCode(), 2, 80).toUpperCase(Locale.ROOT));
        cmd.setReason(requireText(cmd.getReason(), 2, 500));
        requireIdempotencyKey(cmd.getIdempotencyKey());
        if (cmd.getContent() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return cmd;
    }

    private static CreateVersionCmd requireCreateVersion(CreateVersionCmd cmd) {
        if (cmd == null || cmd.getExpectedPlaybookVersion() == null || cmd.getExpectedPlaybookVersion() < 0
                || cmd.getContent() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        cmd.setReason(requireText(cmd.getReason(), 2, 500));
        requireIdempotencyKey(cmd.getIdempotencyKey());
        return cmd;
    }

    private static VersionTransitionCmd requireVersionTransition(VersionTransitionCmd cmd) {
        if (cmd == null || cmd.getExpectedPlaybookVersion() == null || cmd.getExpectedPlaybookVersion() < 0
                || !isHash(cmd.getExpectedContentHash())) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        cmd.setReason(requireText(cmd.getReason(), 2, 500));
        requireIdempotencyKey(cmd.getIdempotencyKey());
        return cmd;
    }

    private static BindCasePlaybookCmd requireBind(BindCasePlaybookCmd cmd) {
        if (cmd == null || cmd.getPlaybookVersionId() == null || cmd.getExpectedCaseVersion() == null
                || cmd.getExpectedGovernanceFactVersion() == null || cmd.getExpectedCaseVersion() < 0
                || cmd.getExpectedGovernanceFactVersion() < 0 || !isHash(cmd.getExpectedGovernanceSnapshotEtag())
                || !isHash(cmd.getExpectedContentHash())) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        cmd.setNote(requireText(cmd.getNote(), 2, 500));
        requireIdempotencyKey(cmd.getIdempotencyKey());
        return cmd;
    }

    private static CasePlaybookTransitionCmd requireCaseTransition(CasePlaybookTransitionCmd cmd) {
        if (cmd == null || cmd.getExpectedBindingVersion() == null || cmd.getExpectedCaseVersion() == null
                || cmd.getExpectedGovernanceFactVersion() == null || cmd.getExpectedBindingVersion() < 0
                || cmd.getExpectedCaseVersion() < 0 || cmd.getExpectedGovernanceFactVersion() < 0
                || !isHash(cmd.getExpectedGovernanceSnapshotEtag())) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        cmd.setNote(requireText(cmd.getNote(), 2, 500));
        requireIdempotencyKey(cmd.getIdempotencyKey());
        return cmd;
    }

    private static VerifyCheckCmd requireVerify(VerifyCheckCmd cmd) {
        requireCaseTransition(cmd);
        if (cmd.getExpectedCheckVersion() == null || cmd.getExpectedCheckVersion() < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return cmd;
    }

    private static void requirePlaybookVersion(ChannelQualityGovernancePlaybookDTO playbook, Integer expected) {
        if (!expected.equals(playbook.getPlaybookVersion())) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    private static ChannelQualityGovernancePlaybookDTO requirePlaybook(ChannelQualityGovernancePlaybookDTO playbook) {
        if (playbook == null || playbook.getId() == null || playbook.getId() <= 0
                || !StringUtils.hasText(playbook.getPlaybookCode()) || playbook.getLatestVersionNo() == null
                || playbook.getLatestVersionNo() < 0 || playbook.getPlaybookVersion() == null
                || playbook.getPlaybookVersion() < 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return playbook;
    }

    private static ChannelQualityGovernancePlaybookDTO.VersionDTO requireVersion(
            ChannelQualityGovernancePlaybookDTO.VersionDTO version) {
        if (version == null || version.getId() == null || version.getId() <= 0 || version.getPlaybookId() == null
                || version.getPlaybookId() <= 0 || version.getVersionNo() == null || version.getVersionNo() <= 0
                || !Set.of("DRAFT", "PUBLISHED", "RETIRED").contains(version.getStatus())
                || !StringUtils.hasText(version.getCanonicalContentJson()) || !isHash(version.getContentHash())) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return version;
    }

    private static ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO requireCasePlaybook(
            ChannelQualityGovernancePlaybookDTO.CasePlaybookDTO binding) {
        if (binding == null || binding.getId() == null || binding.getId() <= 0 || binding.getCaseId() == null
                || binding.getCaseId() <= 0 || binding.getPlaybookId() == null || binding.getPlaybookVersionId() == null
                || binding.getBindingVersion() == null || binding.getBindingVersion() < 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return binding;
    }

    private static String requiredJsonText(JsonNode node, String field) {
        String value = node.path(field).asText();
        return requireText(value, 2, 500);
    }

    private JsonNode readJson(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.DEPENDENCY_ERROR);
        }
    }

    private static int rankWeight(String result) {
        return "MATCHED".equals(result) ? 2 : "PARTIAL".equals(result) ? 1 : 0;
    }

    private static boolean available(ChannelQualityRiskGovernanceSnapshotQueryFacade.SnapshotTime time) {
        return time != null && "AVAILABLE".equals(time.availability()) && time.value() != null;
    }

    private static boolean isHash(String value) {
        return value != null && value.matches("^sha256:[0-9a-f]{64}$");
    }

    private static void requireIdempotencyKey(String value) {
        if (!StringUtils.hasText(value) || value.trim().length() < 8 || value.trim().length() > 96) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static String requireText(String value, int min, int max) {
        if (!StringUtils.hasText(value) || value.trim().length() < min || value.trim().length() > max) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value.trim();
    }

    private static long requireId(Long value) {
        if (value == null || value <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return value;
    }

    private static void requireOperator(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static void requireWrite(int updated) {
        if (updated != 1) {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    private static String fingerprint(Object... values) {
        StringBuilder source = new StringBuilder();
        for (Object value : values) {
            String text = value == null ? "<null>" : String.valueOf(value);
            source.append(text.length()).append(':').append(text).append('|');
        }
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(source.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder("sha256:");
            for (byte item : bytes) {
                hash.append(String.format("%02x", item));
            }
            return hash.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private record Applicability(String result, List<String> reasonCodes) {
    }
}
