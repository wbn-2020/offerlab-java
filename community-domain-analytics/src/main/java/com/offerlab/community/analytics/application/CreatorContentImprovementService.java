package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.dto.CreatorContentImprovementSignalsDTO;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.db.MigrationCheckService;
import com.offerlab.community.post.api.ContentMaintenanceTaskReadFacade;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CreatorContentImprovementService {

    static final int PERIOD_DAYS = 30;
    static final int MINIMUM_DISTINCT_READERS = 5;
    private static final int MAX_PAGE_SIZE = 10;
    private static final int MAX_POST_SCAN_COUNT = 250;

    private final PostFacade postFacade;
    private final RevisionAwareQualitySignalCoordinator qualitySignalCoordinator;
    private final ContentMaintenanceTaskReadFacade maintenanceTaskReadFacade;
    private final MigrationCheckService migrationCheckService;

    public CreatorContentImprovementSignalsDTO list(Long uid, String cursor, int requestedSize) {
        requireUser(uid);
        if (!migrationCheckService.creatorContentRevisionBoundaryReady()) {
            return unavailableSignals("QUALITY_SIGNAL_SCHEMA_UNAVAILABLE");
        }
        int pageSize = Math.min(Math.max(requestedSize, 1), MAX_PAGE_SIZE);
        long postCursor = parseCursor(cursor);
        RevisionAwareQualitySignalCoordinator.Window qualityWindow = qualitySignalCoordinator.captureWindow();
        List<CreatorContentImprovementSignalsDTO.Item> items = new ArrayList<>();
        String nextCursor = null;
        boolean hasMore = false;
        int scanned = 0;

        while (items.size() < pageSize && scanned < MAX_POST_SCAN_COUNT) {
            int scanSize = Math.min(pageSize - items.size(), MAX_POST_SCAN_COUNT - scanned);
            PageResult<PostBriefDTO> page = postFacade.getPostsByAuthor(uid, postCursor, scanSize);
            List<PostBriefDTO> candidates = page == null || page.getItems() == null
                    ? List.of()
                    : page.getItems();
            scanned += page == null || page.getItems() == null ? 0 : page.getItems().size();

            RevisionAwareQualitySignalCoordinator.Resolution qualitySignals =
                    qualitySignalCoordinator.resolveAuthorOwned(
                            qualityWindow, uid, candidates.stream().map(PostBriefDTO::getId).toList());
            if (!qualitySignals.available()) {
                return unavailableSignals("QUALITY_SIGNAL_SOURCE_UNAVAILABLE");
            }

            List<Long> currentRevisionQualifiedPostIds = candidates.stream()
                    .map(PostBriefDTO::getId)
                    .filter(postId -> {
                        RevisionAwareQualitySignalCoordinator.Assessment assessment =
                                qualitySignals.assessments().get(postId);
                        return assessment != null && assessment.currentRevisionQualified();
                    })
                    .toList();
            Set<Long> maintenancePostIds = currentRevisionQualifiedPostIds.isEmpty()
                    ? Set.of()
                    : maintenanceTaskReadFacade.findActivePublicSourcePostIds(uid, currentRevisionQualifiedPostIds);
            maintenancePostIds = maintenancePostIds == null ? Set.of() : maintenancePostIds;
            for (PostBriefDTO post : candidates) {
                RevisionAwareQualitySignalCoordinator.Assessment assessment =
                        qualitySignals.assessments().get(post.getId());
                if (assessment == null) {
                    continue;
                }
                if (assessment.currentRevisionQualified()) {
                    items.add(maintenancePostIds.contains(post.getId())
                            ? toMaintenanceItem(post)
                            : toReviewItem(post));
                } else if (assessment.updatedAwaitingAnonymousFeedback()) {
                    items.add(toUpdatedAwaitingFeedbackItem(post));
                }
            }

            hasMore = page != null && Boolean.TRUE.equals(page.getHasMore());
            nextCursor = page == null ? null : page.getNextCursor();
            if (!hasMore || nextCursor == null || nextCursor.isBlank()) {
                break;
            }
            long parsedNext = parseCursor(nextCursor);
            if (parsedNext <= 0 || parsedNext == postCursor) {
                break;
            }
            postCursor = parsedNext;
        }

        return CreatorContentImprovementSignalsDTO.builder()
                .periodDays(PERIOD_DAYS)
                .degraded(false)
                .items(items)
                .nextCursor(hasMore ? nextCursor : null)
                .hasMore(hasMore)
                .build();
    }

    private static CreatorContentImprovementSignalsDTO unavailableSignals(String fallbackReason) {
        return CreatorContentImprovementSignalsDTO.builder()
                .periodDays(PERIOD_DAYS)
                .degraded(true)
                .fallbackReason(fallbackReason)
                .items(List.of())
                .build();
    }

    private static CreatorContentImprovementSignalsDTO.Item toReviewItem(PostBriefDTO post) {
        Long postId = post.getId();
        return CreatorContentImprovementSignalsDTO.Item.builder()
                .postId(postId)
                .postTitle(compact(post.getTitle(), 160))
                .domain(post.getDomain())
                .domainName(domainName(post.getDomain()))
                .state("REVIEW_RECOMMENDED")
                .headline("近 30 天出现了足够匿名的质量复核信号")
                .detail("建议检查标题、背景、过程和结论是否完整。")
                .postHref("/post/" + postId)
                .editHref(editHref(postId))
                .build();
    }

    private static CreatorContentImprovementSignalsDTO.Item toMaintenanceItem(PostBriefDTO post) {
        Long postId = post.getId();
        return CreatorContentImprovementSignalsDTO.Item.builder()
                .postId(postId)
                .postTitle(compact(post.getTitle(), 160))
                .domain(post.getDomain())
                .domainName(domainName(post.getDomain()))
                .state("MAINTENANCE_EXISTS")
                .headline("该公开内容已有维护工作在处理")
                .detail("请在既有维护工作区查看可处理的公开内容。")
                .postHref("/post/" + postId)
                .editHref(editHref(postId))
                .workspaceHref("/me/maintenance")
                .build();
    }

    private static CreatorContentImprovementSignalsDTO.Item toUpdatedAwaitingFeedbackItem(PostBriefDTO post) {
        Long postId = post.getId();
        return CreatorContentImprovementSignalsDTO.Item.builder()
                .postId(postId)
                .postTitle(compact(post.getTitle(), 160))
                .domain(post.getDomain())
                .domainName(domainName(post.getDomain()))
                .state("UPDATED_AWAITING_ANONYMOUS_FEEDBACK")
                .headline("内容已更新，等待新的匿名反馈")
                .detail("旧版本的匿名复核信号不再代表当前内容；平台尚不能判断问题是否已解决。")
                .postHref("/post/" + postId)
                .editHref(editHref(postId))
                .build();
    }

    private static String editHref(Long postId) {
        return "/editor/" + postId + "?source=creator_workbench";
    }

    private static void requireUser(Long uid) {
        if (uid == null || uid <= 0) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank() || "0".equals(cursor.trim())) {
            return 0L;
        }
        try {
            long value = Long.parseLong(cursor.trim());
            if (value <= 0) {
                throw new NumberFormatException("cursor must be positive");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private static String compact(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return "未命名公开内容";
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static String domainName(Integer domain) {
        return switch (domain == null ? 0 : domain) {
            case 1 -> "科技数码";
            case 2 -> "职场经验";
            case 3 -> "学习成长";
            case 4 -> "生活方式";
            case 5 -> "投资理财";
            default -> "未分类";
        };
    }
}
