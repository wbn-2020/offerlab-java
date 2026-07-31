package com.offerlab.community.post.reference.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.security.ExternalUrlSafety;
import com.offerlab.community.post.api.dto.PostReferenceCreateCmd;
import com.offerlab.community.post.api.dto.PostReferenceDTO;
import com.offerlab.community.post.api.dto.PostReferenceReorderCmd;
import com.offerlab.community.post.api.dto.PostReferenceReorderItemCmd;
import com.offerlab.community.post.api.dto.PostReferenceUpdateCmd;
import com.offerlab.community.post.reference.infrastructure.persistence.PostReferenceMapper;
import com.offerlab.community.post.reference.infrastructure.persistence.PostReferenceRows.PostAccessRow;
import com.offerlab.community.post.reference.infrastructure.persistence.PostReferenceRows.ReferenceRow;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PostReferenceService {

    static final String ACTIVE = "ACTIVE";
    static final String BROKEN = "BROKEN";
    private static final Set<String> REFERENCE_TYPES = Set.of("SOURCE", "EXAMPLE", "DATA", "FOLLOW_UP");
    private static final int MAX_URL_LENGTH = 2048;

    private final PostReferenceMapper referenceMapper;
    private final SnowflakeIdGenerator idGenerator;

    public List<PostReferenceDTO> listPublic(Long postId) {
        requireId(postId);
        requirePublicPost(postId);
        List<ReferenceRow> rows = referenceMapper.selectActiveByPostId(postId);
        requirePublicPost(postId);
        return rows == null ? List.of() : rows.stream().map(PostReferenceService::toDto).toList();
    }

    @Transactional
    public PostReferenceDTO create(Long postId, PostReferenceCreateCmd cmd, Long operatorUid) {
        PostAccessRow post = requireOwnedPost(postId, operatorUid);
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        NormalizedInput input = normalize(cmd.getReferenceType(), cmd.getTitle(), cmd.getUrl(),
                cmd.getNote(), cmd.getReferenceStatus(), cmd.getBrokenReason());
        ensureUrlAvailable(postId, input.normalizedUrl(), null);
        LocalDateTime now = LocalDateTime.now();
        ReferenceRow row = new ReferenceRow();
        row.setId(idGenerator.nextId());
        row.setPostId(postId);
        row.setOwnerUid(post.getAuthorId());
        row.setReferenceType(input.referenceType());
        row.setTitle(input.title());
        row.setUrl(input.url());
        row.setNormalizedUrl(input.normalizedUrl());
        row.setSourceDomain(input.sourceDomain());
        row.setNote(input.note());
        row.setBrokenReason(input.brokenReason());
        row.setReferenceStatus(input.referenceStatus());
        row.setSortOrder(referenceMapper.selectMaxSortOrder(postId) + 1);
        row.setRevision(1);
        row.setLastConfirmedAt(ACTIVE.equals(input.referenceStatus()) ? now : null);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        try {
            referenceMapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw duplicateUrl();
        }
        return toDto(row);
    }

    @Transactional
    public PostReferenceDTO update(Long postId, Long referenceId, PostReferenceUpdateCmd cmd, Long operatorUid) {
        PostAccessRow post = requireOwnedPost(postId, operatorUid);
        ReferenceRow existing = requireOwnedReference(postId, referenceId, post.getAuthorId());
        if (cmd == null) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (cmd.getExpectedRevision() == null || cmd.getExpectedRevision() <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        NormalizedInput input = normalize(cmd.getReferenceType(), cmd.getTitle(), cmd.getUrl(),
                cmd.getNote(), cmd.getReferenceStatus(), cmd.getBrokenReason());
        ensureUrlAvailable(postId, input.normalizedUrl(), referenceId);
        existing.setReferenceType(input.referenceType());
        existing.setTitle(input.title());
        existing.setUrl(input.url());
        existing.setNormalizedUrl(input.normalizedUrl());
        existing.setSourceDomain(input.sourceDomain());
        existing.setNote(input.note());
        existing.setBrokenReason(input.brokenReason());
        existing.setReferenceStatus(input.referenceStatus());
        existing.setLastConfirmedAt(ACTIVE.equals(input.referenceStatus()) ? LocalDateTime.now() : null);
        existing.setUpdateTime(LocalDateTime.now());
        try {
            if (referenceMapper.updateIfRevision(existing, cmd.getExpectedRevision()) != 1) {
                throw staleReference();
            }
        } catch (DuplicateKeyException e) {
            throw duplicateUrl();
        }
        existing.setRevision(cmd.getExpectedRevision() + 1);
        return toDto(existing);
    }

    @Transactional
    public void delete(Long postId, Long referenceId, Integer expectedRevision, Long operatorUid) {
        PostAccessRow post = requireOwnedPost(postId, operatorUid);
        requireOwnedReference(postId, referenceId, post.getAuthorId());
        if (expectedRevision == null || expectedRevision <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (referenceMapper.softDeleteIfRevision(postId, referenceId, post.getAuthorId(),
                expectedRevision, LocalDateTime.now()) != 1) {
            throw staleReference();
        }
    }

    @Transactional
    public List<PostReferenceDTO> reorder(Long postId, PostReferenceReorderCmd cmd, Long operatorUid) {
        PostAccessRow post = requireOwnedPost(postId, operatorUid);
        if (cmd == null || cmd.getItems() == null || cmd.getItems().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        List<ReferenceRow> active = referenceMapper.selectActiveByPostId(postId);
        if (active == null) {
            active = List.of();
        }
        List<PostReferenceReorderItemCmd> items = cmd.getItems();
        Set<Long> activeIds = active.stream().map(ReferenceRow::getId).collect(java.util.stream.Collectors.toSet());
        Set<Long> requestedIds = new HashSet<>();
        Map<Long, Integer> expectedRevisions = new HashMap<>();
        for (PostReferenceReorderItemCmd item : items) {
            if (item == null || item.getReferenceId() == null || item.getReferenceId() <= 0
                    || item.getExpectedRevision() == null || item.getExpectedRevision() <= 0
                    || !requestedIds.add(item.getReferenceId())) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
            expectedRevisions.put(item.getReferenceId(), item.getExpectedRevision());
        }
        if (!activeIds.equals(requestedIds)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "Submit the complete order of all active references");
        }
        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < items.size(); index++) {
            PostReferenceReorderItemCmd item = items.get(index);
            if (referenceMapper.reorderIfRevision(postId, item.getReferenceId(), post.getAuthorId(),
                    index, expectedRevisions.get(item.getReferenceId()), now) != 1) {
                throw staleReference();
            }
        }
        List<ReferenceRow> reordered = referenceMapper.selectActiveByPostId(postId);
        return (reordered == null ? List.<ReferenceRow>of() : reordered).stream()
                .map(PostReferenceService::toDto)
                .toList();
    }

    private PostAccessRow requireOwnedPost(Long postId, Long operatorUid) {
        requireId(postId);
        if (operatorUid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        PostAccessRow post = referenceMapper.selectPostForUpdate(postId);
        if (post == null || Objects.equals(post.getIsDeleted(), 1)) {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
        if (!Objects.equals(post.getAuthorId(), operatorUid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return post;
    }

    private ReferenceRow requireOwnedReference(Long postId, Long referenceId, Long ownerUid) {
        requireId(referenceId);
        ReferenceRow row = referenceMapper.selectActiveById(postId, referenceId);
        if (row == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!Objects.equals(row.getOwnerUid(), ownerUid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return row;
    }

    private void requirePublicPost(Long postId) {
        if (referenceMapper.countPublicPost(postId) != 1) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void ensureUrlAvailable(Long postId, String normalizedUrl, Long currentId) {
        Long existingId = referenceMapper.selectActiveIdByNormalizedUrl(postId, normalizedUrl);
        if (existingId != null && !Objects.equals(existingId, currentId)) {
            throw duplicateUrl();
        }
    }

    private static NormalizedInput normalize(String referenceType, String title, String url,
                                             String note, String status, String brokenReason) {
        String type = upperRequired(referenceType, REFERENCE_TYPES);
        String cleanTitle = requiredText(title, 255);
        String cleanNote = optionalText(note, 1000);
        String cleanStatus = status == null ? ACTIVE : upperRequired(status, Set.of(ACTIVE, BROKEN));
        String cleanBrokenReason = optionalText(brokenReason, 500);
        if (BROKEN.equals(cleanStatus) && !StringUtils.hasText(cleanBrokenReason)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    "brokenReason is required for BROKEN references");
        }
        if (ACTIVE.equals(cleanStatus)) {
            cleanBrokenReason = null;
        }
        String cleanUrl = ExternalUrlSafety.requireSafeHttpUrl(requiredText(url, MAX_URL_LENGTH), "url", MAX_URL_LENGTH);
        String normalizedUrl = normalizeAsciiUrl(cleanUrl);
        String sourceDomain = sourceDomain(cleanUrl);
        return new NormalizedInput(type, cleanTitle, cleanUrl, normalizedUrl, sourceDomain,
                cleanNote, cleanBrokenReason, cleanStatus);
    }

    static String normalizeAsciiUrl(String value) {
        try {
            URI uri = new URI(value).normalize();
            String host = asciiHost(uri.getHost());
            String authorityHost = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
            int port = uri.getPort();
            boolean defaultPort = ("http".equalsIgnoreCase(uri.getScheme()) && port == 80)
                    || ("https".equalsIgnoreCase(uri.getScheme()) && port == 443);
            StringBuilder raw = new StringBuilder()
                    .append(uri.getScheme().toLowerCase(Locale.ROOT))
                    .append("://")
                    .append(authorityHost);
            if (port >= 0 && !defaultPort) {
                raw.append(':').append(port);
            }
            raw.append(StringUtils.hasText(uri.getRawPath()) ? uri.getRawPath() : "/");
            if (uri.getRawQuery() != null) {
                raw.append('?').append(uri.getRawQuery());
            }
            String normalized = new URI(raw.toString()).toASCIIString();
            if (normalized.length() > MAX_URL_LENGTH || !isAscii(normalized)) {
                throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "url is not a valid ASCII URL");
            }
            return normalized;
        } catch (BizException e) {
            throw e;
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "url is not a valid ASCII URL");
        }
    }

    private static String asciiHost(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "url is not a valid external URL");
        }
        String host = value;
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        try {
            if (host.contains(":")) {
                return host.toLowerCase(Locale.ROOT);
            }
            String ascii = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            return ascii.endsWith(".") ? ascii.substring(0, ascii.length() - 1) : ascii;
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "url is not a valid ASCII URL");
        }
    }

    private static String sourceDomain(String url) {
        try {
            return asciiHost(new URI(url).getHost());
        } catch (URISyntaxException e) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "url is not a valid ASCII URL");
        }
    }

    private static String requiredText(String value, int maxLength) {
        String normalized = value == null ? null : value.trim();
        if (!StringUtils.hasText(normalized) || normalized.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static String optionalText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static String upperRequired(String value, Set<String> allowed) {
        String normalized = value == null ? null : value.trim().toUpperCase(Locale.ROOT);
        if (normalized == null || !allowed.contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return normalized;
    }

    private static boolean isAscii(String value) {
        return value.chars().allMatch(ch -> ch >= 0x20 && ch <= 0x7e);
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static BizException duplicateUrl() {
        return new BizException(ErrorCode.DUPLICATE_OPERATION.getCode(),
                "A reference with the same URL already exists for this post");
    }

    private static BizException staleReference() {
        return new BizException(ErrorCode.INVALID_STATUS.getCode(),
                "Reference changed, please refresh and retry");
    }

    private static PostReferenceDTO toDto(ReferenceRow row) {
        return PostReferenceDTO.builder()
                .id(row.getId())
                .postId(row.getPostId())
                .ownerUid(row.getOwnerUid())
                .referenceType(row.getReferenceType())
                .title(row.getTitle())
                .url(row.getUrl())
                .normalizedUrl(row.getNormalizedUrl())
                .sourceDomain(row.getSourceDomain())
                .note(row.getNote())
                .brokenReason(row.getBrokenReason())
                .referenceStatus(row.getReferenceStatus())
                .sortOrder(row.getSortOrder())
                .revision(row.getRevision())
                .lastConfirmedAt(row.getLastConfirmedAt())
                .createTime(row.getCreateTime())
                .updateTime(row.getUpdateTime())
                .build();
    }

    private record NormalizedInput(String referenceType, String title, String url, String normalizedUrl,
                                   String sourceDomain, String note, String brokenReason, String referenceStatus) {
    }
}
