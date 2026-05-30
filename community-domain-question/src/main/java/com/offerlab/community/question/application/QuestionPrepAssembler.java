package com.offerlab.community.question.application;

import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.question.api.dto.CompanyPrepDTO;
import com.offerlab.community.question.api.dto.PrepTargetDTO;
import com.offerlab.community.question.api.dto.QuestionDTO;
import com.offerlab.community.question.infrastructure.persistence.po.UserQuestionProgressPO;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class QuestionPrepAssembler {

    public CompanyPrepDTO.UserPrepSummaryDTO progressSummary(Map<Long, UserQuestionProgressPO> progress) {
        Collection<UserQuestionProgressPO> values = progress == null ? List.of() : progress.values();
        return CompanyPrepDTO.UserPrepSummaryDTO.builder()
                .favoriteCount(values.stream().filter(p -> Objects.equals(p.getFavorite(), 1)).count())
                .learningCount(values.stream().filter(p -> "learning".equals(p.getProgressStatus())).count())
                .masteredCount(values.stream().filter(p -> "mastered".equals(p.getProgressStatus())).count())
                .reviewCount(values.stream().filter(p -> "review".equals(p.getProgressStatus())).count())
                .build();
    }

    public List<CompanyPrepDTO.ChecklistItemDTO> companyPrepChecklist(String company,
                                                                      Long viewerUid,
                                                                      boolean targetAdded,
                                                                      List<QuestionDTO> topQuestions,
                                                                      List<PostBriefDTO> recentPosts,
                                                                      List<CompanyPrepDTO.NameCountDTO> hotPositions,
                                                                      List<CompanyPrepDTO.NameCountDTO> topTags,
                                                                      CompanyPrepDTO.UserPrepSummaryDTO progress) {
        int questionCount = topQuestions == null ? 0 : topQuestions.size();
        int postCount = recentPosts == null ? 0 : recentPosts.size();
        int positionCount = hotPositions == null ? 0 : hotPositions.size();
        int tagCount = topTags == null ? 0 : topTags.size();
        int mastered = progress == null ? 0 : progress.getMasteredCount().intValue();
        int active = progress == null ? 0 : (int) (progress.getLearningCount() + progress.getReviewCount() + progress.getFavoriteCount());
        String prepAction = "/companies/" + urlEncode(company) + "/prep";
        boolean personalTargetDone = viewerUid != null && targetAdded;
        return List.of(
                checklistItem("target", "加入我的目标", "把公司加入准备台，后续推荐会优先匹配这家公司。", personalTargetDone,
                        personalTargetDone ? 1 : 0, 1, viewerUid == null ? "/login?redirect=" + urlEncode(prepAction) : "/me/prep"),
                checklistItem("questions", "覆盖高频题", "至少沉淀 5 道公司相关高频题，方便面试前集中刷。", questionCount >= 5,
                        questionCount, 5, "/questions?company=" + urlEncode(company)),
                checklistItem("posts", "阅读近期面经", "至少阅读 3 篇近期面经，了解轮次和问题风格。", postCount >= 3,
                        postCount, 3, "/search?q=" + urlEncode(company)),
                checklistItem("progress", "掌握核心问题", "至少掌握 3 道高频题，形成自己的答题模板。", mastered >= 3,
                        mastered, 3, "/questions?company=" + urlEncode(company)),
                checklistItem("focus", "锁定岗位和标签", "准备包需要有岗位和技术标签，便于拆分复习方向。", positionCount > 0 && tagCount >= 3,
                        Math.min(positionCount + tagCount, 6), 6, "/questions?company=" + urlEncode(company)),
                checklistItem("activity", "保持复习动作", "收藏、学习中或待复习题越多，说明这家公司正在进入你的备考节奏。", active >= 3,
                        active, 3, "/questions?company=" + urlEncode(company))
        );
    }

    public int prepScore(List<CompanyPrepDTO.ChecklistItemDTO> checklist) {
        if (checklist == null || checklist.isEmpty()) {
            return 0;
        }
        long done = checklist.stream().filter(item -> Boolean.TRUE.equals(item.getDone())).count();
        return (int) Math.round(done * 100.0 / checklist.size());
    }

    public List<String> nextActions(List<CompanyPrepDTO.ChecklistItemDTO> checklist) {
        if (checklist == null || checklist.isEmpty()) {
            return List.of();
        }
        return checklist.stream()
                .filter(item -> !Boolean.TRUE.equals(item.getDone()))
                .map(CompanyPrepDTO.ChecklistItemDTO::getTitle)
                .filter(title -> title != null && !title.isBlank())
                .limit(3)
                .toList();
    }

    public long favoriteCount(Map<Long, UserQuestionProgressPO> progress) {
        return countByStatusOrFavorite(progress, null, true);
    }

    public long learningCount(Map<Long, UserQuestionProgressPO> progress) {
        return countByStatusOrFavorite(progress, "learning", false);
    }

    public long masteredCount(Map<Long, UserQuestionProgressPO> progress) {
        return countByStatusOrFavorite(progress, "mastered", false);
    }

    public long reviewCount(Map<Long, UserQuestionProgressPO> progress) {
        return countByStatusOrFavorite(progress, "review", false);
    }

    public UserPrepSummary targetSummary(PrepTargetDTO target,
                                         int questionCount,
                                         List<QuestionDTO> recommendedQuestions,
                                         Map<Long, UserQuestionProgressPO> progress) {
        return new UserPrepSummary(target, questionCount, favoriteCount(progress), learningCount(progress),
                masteredCount(progress), reviewCount(progress), recommendedQuestions == null ? List.of() : recommendedQuestions);
    }

    private long countByStatusOrFavorite(Map<Long, UserQuestionProgressPO> progress, String status, boolean favorite) {
        if (progress == null || progress.isEmpty()) {
            return 0;
        }
        return progress.values().stream()
                .filter(p -> favorite ? Objects.equals(p.getFavorite(), 1) : status.equals(p.getProgressStatus()))
                .count();
    }

    private CompanyPrepDTO.ChecklistItemDTO checklistItem(String key, String title, String description, boolean done,
                                                          int current, int target, String actionHref) {
        return CompanyPrepDTO.ChecklistItemDTO.builder()
                .key(key)
                .title(title)
                .description(description)
                .done(done)
                .current(Math.max(0, current))
                .target(Math.max(1, target))
                .actionHref(actionHref)
                .build();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public record UserPrepSummary(PrepTargetDTO target,
                                  int questionCount,
                                  long favoriteCount,
                                  long learningCount,
                                  long masteredCount,
                                  long reviewCount,
                                  List<QuestionDTO> recommendedQuestions) {
    }
}
