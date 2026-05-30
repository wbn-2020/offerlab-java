package com.offerlab.community.question.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.redis.cache.CacheKeyBuilder;
import com.offerlab.community.infra.redis.cache.MultiLevelCache;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.api.dto.PostBriefDTO;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.domain.model.Post;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.mapper.TagMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import com.offerlab.community.post.infrastructure.persistence.projection.PostTagView;
import com.offerlab.community.question.api.dto.AiTaskDetailDTO;
import com.offerlab.community.question.api.dto.AiTaskDTO;
import com.offerlab.community.question.api.dto.CompanyAliasCmd;
import com.offerlab.community.question.api.dto.CompanyAliasCandidateDTO;
import com.offerlab.community.question.api.dto.CompanyAliasDTO;
import com.offerlab.community.question.api.dto.CompanyPrepDTO;
import com.offerlab.community.question.api.dto.PostQuestionBlockDTO;
import com.offerlab.community.question.api.dto.PrepTargetCmd;
import com.offerlab.community.question.api.dto.PrepTargetDTO;
import com.offerlab.community.question.api.dto.QuestionAdminUpdateCmd;
import com.offerlab.community.question.api.dto.QuestionAdminQuery;
import com.offerlab.community.question.api.dto.QuestionDTO;
import com.offerlab.community.question.api.dto.QuestionDetailDTO;
import com.offerlab.community.question.api.dto.QuestionDuplicateCandidateDTO;
import com.offerlab.community.question.api.dto.QuestionDuplicateGroupDTO;
import com.offerlab.community.question.api.dto.QuestionQuery;
import com.offerlab.community.question.api.dto.QuestionTagDTO;
import com.offerlab.community.question.api.dto.UserPrepOverviewDTO;
import com.offerlab.community.question.api.dto.UserWeeklyPrepReportDTO;
import com.offerlab.community.question.api.event.QuestionExtractionFinishedEvent;
import com.offerlab.community.question.infrastructure.persistence.mapper.AiExtractTaskMapper;
import com.offerlab.community.question.infrastructure.persistence.mapper.CompanyAliasMapper;
import com.offerlab.community.question.infrastructure.persistence.mapper.InterviewQuestionMapper;
import com.offerlab.community.question.infrastructure.persistence.mapper.InterviewQuestionTagMapper;
import com.offerlab.community.question.infrastructure.persistence.mapper.MockInterviewSessionMapper;
import com.offerlab.community.question.infrastructure.persistence.mapper.UserQuestionProgressMapper;
import com.offerlab.community.question.infrastructure.persistence.mapper.UserPrepTargetMapper;
import com.offerlab.community.question.infrastructure.persistence.po.AiExtractTaskPO;
import com.offerlab.community.question.infrastructure.persistence.po.CompanyAliasPO;
import com.offerlab.community.question.infrastructure.persistence.po.InterviewQuestionPO;
import com.offerlab.community.question.infrastructure.persistence.po.UserQuestionProgressPO;
import com.offerlab.community.question.infrastructure.persistence.po.UserPrepTargetPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionFacadeImpl implements QuestionFacade {
    private static final Set<String> TECHNICAL_KEYWORDS = Set.of(
            "redis", "缓存", "一致", "mysql", "索引", "事务", "锁", "并发", "线程", "jvm",
            "spring", "kafka", "mq", "消息", "es", "elasticsearch", "数据库", "分布式",
            "限流", "熔断", "降级", "幂等", "hashmap", "集合", "网络", "http", "tcp",
            "垃圾回收", "gc", "内存", "高可用", "高并发", "设计模式"
    );
    private static final Set<String> SEMANTIC_STOP_TOKENS = Set.of(
            "什么", "一下", "哪些", "区别", "介绍", "面试", "项目", "系统", "业务", "场景", "时候", "进行", "一个"
    );

    private final InterviewQuestionMapper questionMapper;
    private final InterviewQuestionTagMapper questionTagMapper;
    private final AiExtractTaskMapper taskMapper;
    private final UserQuestionProgressMapper progressMapper;
    private final UserPrepTargetMapper prepTargetMapper;
    private final CompanyAliasMapper companyAliasMapper;
    private final MockInterviewSessionMapper mockInterviewSessionMapper;
    private final PostMapper postMapper;
    private final TagMapper tagMapper;
    private final PostFacade postFacade;
    private final QuestionExtractor questionExtractor;
    private final SnowflakeIdGenerator idGen;
    private final ObjectMapper objectMapper;
    private final MultiLevelCache<CompanyPrepDTO> companyPrepCache;
    private final ApplicationEventPublisher events;
    private final PlatformTransactionManager transactionManager;
    private final QuestionSearchIndexer questionSearchIndexer;
    private final QuestionPrepAssembler prepAssembler;

    @Override
    @Transactional
    public Long extractPostQuestions(Long postId, boolean manual) {
        AiExtractTaskPO task = newTask(postId);
        taskMapper.insert(task);
        events.publishEvent(QuestionExtractRequestedEvent.builder()
                .taskId(task.getId())
                .postId(postId)
                .manual(manual)
                .build());
        return task.getId();
    }

    @Override
    public void processExtractTask(Long taskId) {
        AiExtractTaskPO task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        taskMapper.updateStatus(taskId, QuestionConstants.TASK_RUNNING);
        PostDTO post = postFacade.getPost(task.getPostId());
        if (post == null) {
            PostPO rawPost = postMapper.selectById(task.getPostId());
            if (rawPost != null) {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> hidePostQuestions(task.getPostId()));
                succeed(task, 0);
                return;
            }
            fail(task, "post not found or not public visible");
            return;
        }
        if (!Objects.equals(post.getPostType(), Post.TYPE_INTERVIEW)) {
            succeed(task, 0);
            return;
        }
        if (!Objects.equals(post.getVisibility(), Post.VIS_PUBLIC)
                || !Objects.equals(post.getPostStatus(), Post.STATUS_PUBLISHED)) {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> hidePostQuestions(post.getId()));
            succeed(task, 0);
            return;
        }
        try {
            List<ExtractedQuestion> extracted = extractQuestions(post);
            Integer count = new TransactionTemplate(transactionManager).execute(status -> replacePostQuestions(post, extracted));
            succeed(task, count == null ? 0 : count);
            publishExtractionFinished(task, post, true, count == null ? 0 : count, null);
        } catch (Exception e) {
            log.warn("question extraction failed: postId={} taskId={}", post.getId(), task.getId(), e);
            fail(task, e.getMessage());
            publishExtractionFinished(task, post, false, 0, e.getMessage());
        }
    }

    @Override
    public Map<String, Object> rebuildQuestions(int limit) {
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 500));
        List<Long> postIds = postMapper.selectRecentPublicInterviewPostIds(safeLimit);
        postIds.forEach(postId -> extractPostQuestions(postId, true));
        return Map.of("requested", safeLimit, "submitted", postIds.size());
    }

    @Override
    public Map<String, Object> rebuildQuestionIndex() {
        return questionSearchIndexer.rebuildAll();
    }

    @Override
    public List<AiTaskDTO> listTasks(Integer status, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return taskMapper.listRecent(status, safeLimit).stream().map(this::toTaskDto).toList();
    }

    @Override
    public AiTaskDetailDTO getTaskDetail(Long taskId) {
        AiExtractTaskPO task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        PostDTO post = null;
        try {
            post = postFacade.getPost(task.getPostId());
        } catch (Exception e) {
            log.debug("load post for ai task detail failed: taskId={} postId={}", taskId, task.getPostId(), e);
        }
        PostPO rawPost = post == null ? postMapper.selectById(task.getPostId()) : null;
        List<AiTaskDTO> records = taskMapper.listRecentByPost(task.getPostId(), task.getTaskType(), 8)
                .stream()
                .map(this::toTaskDto)
                .toList();
        return AiTaskDetailDTO.builder()
                .task(toTaskDto(task))
                .sourcePostId(task.getPostId())
                .sourcePostTitle(post == null ? rawPost == null ? null : rawPost.getTitle() : post.getTitle())
                .sourcePostSummary(post == null ? rawPost == null ? null : summaryText(rawPost.getContent(), 180) : summaryText(post.getContent(), 180))
                .retryRecords(records)
                .build();
    }

    @Override
    @Transactional
    public AiTaskDTO retryTask(Long taskId) {
        AiExtractTaskPO task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        taskMapper.markForRetry(taskId);
        events.publishEvent(QuestionExtractRequestedEvent.builder()
                .taskId(taskId)
                .postId(task.getPostId())
                .manual(true)
                .build());
        return toTaskDto(taskMapper.selectById(taskId));
    }

    @Override
    public PostQuestionBlockDTO getPostQuestionBlock(Long postId, Long viewerUid, boolean admin) {
        AiExtractTaskPO task = taskMapper.findLatest(postId, QuestionConstants.TASK_TYPE_QUESTION_EXTRACT);
        List<QuestionDTO> questions = toQuestionDtos(questionMapper.selectByPostId(postId, admin), viewerUid);
        String status = task == null ? "none" : taskStatusName(task.getTaskStatus());
        boolean failed = task != null && Objects.equals(task.getTaskStatus(), QuestionConstants.TASK_FAILED);
        return PostQuestionBlockDTO.builder()
                .taskStatus(status)
                .questions(questions)
                .errorVisible(admin && failed)
                .errorMessage(admin && failed ? task.getErrorMessage() : null)
                .canRetry(admin)
                .build();
    }

    @Override
    public PageResult<QuestionDTO> searchQuestions(QuestionQuery query, Long viewerUid) {
        QuestionQuery q = query == null ? new QuestionQuery() : query;
        int pageSize = Math.max(1, Math.min(q.getPageSize() == null ? 20 : q.getPageSize(), 50));
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        int offset = (page - 1) * pageSize;
        String mistakeReason = normalizeOptionalMistakeReason(q.getMistakeReason());
        String progressStatus = normalizeOptionalProgress(q.getProgressStatus());
        boolean hasNote = Boolean.TRUE.equals(q.getHasNote());
        boolean hasAnswerDraft = Boolean.TRUE.equals(q.getHasAnswerDraft());
        boolean hasStarStory = Boolean.TRUE.equals(q.getHasStarStory());
        boolean usePersonalFilter = mistakeReason != null || progressStatus != null || hasNote || hasAnswerDraft || hasStarStory;
        List<Long> tagIds = normalizeTagIds(q.getTagIds());
        if (usePersonalFilter && viewerUid == null) {
            return PageResult.of(List.of(), null, false);
        }
        Map<Long, QuestionSearchIndexer.QuestionSearchHit> highlights = Map.of();
        List<InterviewQuestionPO> rows = questionMapper.searchPublic(
                clean(q.getKeyword()), clean(q.getCompany()), clean(q.getPosition()), clean(q.getDifficulty()),
                clean(q.getRound()), tagIds, q.getStartTime(), q.getEndTime(), normalizeSort(q.getSort()),
                viewerUid, mistakeReason, progressStatus, usePersonalFilter, hasNote, hasAnswerDraft, hasStarStory, offset, pageSize + 1);
        if (!clean(q.getKeyword()).isBlank() && !usePersonalFilter) {
            java.util.Optional<QuestionSearchResult> esResult = searchQuestionsByEs(q, offset, pageSize + 1);
            if (esResult.isPresent()) {
                rows = esResult.get().rows();
                highlights = esResult.get().highlights();
            }
        }
        boolean hasMore = rows.size() > pageSize;
        List<InterviewQuestionPO> pageRows = hasMore ? rows.subList(0, pageSize) : rows;
        return PageResult.of(toQuestionDtos(pageRows, viewerUid, highlights), hasMore ? String.valueOf(page + 1) : null, hasMore);
    }

    @Override
    public QuestionDetailDTO getQuestionDetail(Long questionId, Long viewerUid, boolean admin) {
        return loadQuestionDetail(questionId, viewerUid, admin);
    }

    @Override
    public List<PostBriefDTO> getRelatedPosts(Long questionId) {
        return loadQuestionDetail(questionId, null, false).getSourcePosts();
    }

    @Override
    @Transactional
    public Map<String, Object> favorite(Long questionId, Long uid, boolean favorite) {
        ensureQuestionVisible(questionId);
        UserQuestionProgressPO existing = progressMapper.selectOne(uid, questionId);
        if (existing == null) {
            progressMapper.upsert(idGen.nextId(), uid, questionId, null, favorite ? 1 : 0, null, null, null, null,
                    null, null, 0, 1);
        } else {
            progressMapper.updateFavorite(uid, questionId, favorite ? 1 : 0);
        }
        return Map.of("questionId", questionId, "favorite", favorite);
    }

    @Override
    @Transactional
    public Map<String, Object> updateProgress(Long questionId, Long uid, String status) {
        String normalized = normalizeProgress(status);
        ensureQuestionVisible(questionId);
        UserQuestionProgressPO existing = progressMapper.selectOne(uid, questionId);
        ReviewSchedule schedule = nextReviewSchedule(normalized, existing, LocalDateTime.now());
        if (existing == null) {
            progressMapper.upsert(idGen.nextId(), uid, questionId, normalized, 0, null, null, null, null,
                    schedule.nextReviewAt(), schedule.lastReviewedAt(), schedule.reviewCountDelta(), schedule.reviewIntervalDays());
        } else {
            progressMapper.updateStatusSchedule(uid, questionId, normalized, schedule.nextReviewAt(), schedule.lastReviewedAt(),
                    schedule.reviewCountDelta(), schedule.reviewIntervalDays());
        }
        Map<String, Object> response = new HashMap<>();
        response.put("questionId", questionId);
        response.put("status", normalized);
        response.put("nextReviewAt", schedule.nextReviewAt());
        response.put("lastReviewedAt", schedule.lastReviewedAt());
        response.put("reviewIntervalDays", schedule.reviewIntervalDays());
        response.put("reviewCountDelta", schedule.reviewCountDelta());
        return response;
    }

    @Override
    @Transactional
    public Map<String, Object> updateNote(Long questionId, Long uid, String note, String mistakeReason, String answerDraft, String starStory) {
        ensureQuestionVisible(questionId);
        UserQuestionProgressPO existing = progressMapper.selectOne(uid, questionId);
        String normalized = note == null && existing != null ? existing.getNote() : limit(clean(note), 4000);
        String normalizedReason = mistakeReason == null && existing != null ? existing.getMistakeReason() : normalizeMistakeReason(mistakeReason);
        String normalizedAnswer = answerDraft == null && existing != null ? existing.getAnswerDraft() : limit(clean(answerDraft), 4000);
        String normalizedStory = starStory == null && existing != null ? existing.getStarStory() : limit(clean(starStory), 2000);
        if (existing == null) {
            progressMapper.upsert(idGen.nextId(), uid, questionId, null, 0, normalized, normalizedReason, normalizedAnswer, normalizedStory,
                    null, null, 0, 1);
        } else {
            progressMapper.updateNote(uid, questionId, normalized);
            progressMapper.updateMistakeReason(uid, questionId, normalizedReason);
            progressMapper.updateAnswerDraft(uid, questionId, normalizedAnswer, normalizedStory);
        }
        return Map.of("questionId", questionId,
                "note", normalized == null ? "" : normalized,
                "mistakeReason", normalizedReason == null ? "" : normalizedReason,
                "answerDraft", normalizedAnswer == null ? "" : normalizedAnswer,
                "starStory", normalizedStory == null ? "" : normalizedStory);
    }

    @Override
    public List<String> suggestCompanies(String prefix, int size) {
        String p = clean(prefix).toLowerCase();
        int limit = Math.max(1, Math.min(size <= 0 ? 10 : size, 20));
        LinkedHashSet<String> names = new LinkedHashSet<>();
        questionMapper.suggestCompanies(limit * 2).forEach(row -> addIfMatches(names, row.get("name"), p));
        if (names.size() < limit) {
            companyAliasMapper.selectList(new LambdaQueryWrapper<CompanyAliasPO>()
                    .eq(CompanyAliasPO::getStatus, 1)
                    .last("LIMIT " + (limit * 2)))
                    .forEach(alias -> {
                        addIfMatches(names, alias.getAlias(), p);
                        addIfMatches(names, alias.getCanonicalCompany(), p);
                    });
        }
        return names.stream().limit(limit).toList();
    }

    @Override
    public CompanyPrepDTO getCompanyPrep(String company, Long viewerUid) {
        String canonical = canonicalCompany(company);
        String cacheKey = CacheKeyBuilder.companyPrep(canonical);
        if (viewerUid == null) {
            CompanyPrepDTO cached = companyPrepCache.get(cacheKey, key -> loadCompanyPrep(canonical, null), CompanyPrepDTO.class);
            return cached;
        }
        return loadCompanyPrep(canonical, viewerUid);
    }

    @Override
    public UserPrepOverviewDTO getMyPrepOverview(Long uid) {
        Map<String, Object> overviewCounts = progressMapper.countOverview(uid);
        List<QuestionDTO> favorites = questionsFromProgress(progressMapper.selectRecentFavorites(uid, 8), uid);
        List<QuestionDTO> reviews = questionsFromProgress(progressMapper.selectRecentByStatus(uid, "review", 8), uid);
        List<QuestionDTO> answerDrafts = questionsFromProgress(progressMapper.selectRecentAnswerDrafts(uid, 6), uid);
        List<PrepTargetDTO> targets = listPrepTargets(uid);
        List<QuestionDTO> recommended = recommendedForTargets(targets, uid);
        List<UserPrepOverviewDTO.TargetPrepSummaryDTO> targetSummaries = targetSummaries(targets, uid);
        UserPrepOverviewDTO.ReviewPlanDTO reviewPlan = reviewPlan(uid);
        return UserPrepOverviewDTO.builder()
                .favoriteCount(asLong(overviewCounts.get("favoriteCount")))
                .todoCount(asLong(overviewCounts.get("todoCount")))
                .learningCount(asLong(overviewCounts.get("learningCount")))
                .masteredCount(asLong(overviewCounts.get("masteredCount")))
                .reviewCount(asLong(overviewCounts.get("reviewCount")))
                .noteCount(asLong(overviewCounts.get("noteCount")))
                .answerDraftCount(asLong(overviewCounts.get("answerDraftCount")))
                .targets(targets)
                .favoriteQuestions(favorites)
                .reviewQuestions(reviews)
                .answerDraftQuestions(answerDrafts)
                .recommendedQuestions(recommended)
                .targetSummaries(targetSummaries)
                .mistakeReasonCounts(mistakeReasonCounts(uid))
                .focusTagCounts(focusTagCounts(uid))
                .reviewPlan(reviewPlan)
                .build();
    }

    @Override
    public UserWeeklyPrepReportDTO getMyWeeklyPrepReport(Long uid) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusDays(7);
        Map<String, Object> progress = progressMapper.summarizeWeeklyReport(uid, since);
        Map<String, Object> mock = mockInterviewSessionMapper.summarizeWeeklyReport(uid, since);
        List<QuestionDTO> touchedQuestions = questionsFromProgress(progressMapper.selectUpdatedSince(uid, since, 8), uid);
        List<UserPrepOverviewDTO.MistakeReasonCountDTO> mistakes = mistakeReasonCounts(uid).stream().limit(5).toList();
        List<UserPrepOverviewDTO.FocusTagCountDTO> focusTags = focusTagCounts(uid).stream().limit(5).toList();
        int mastered = asInt(progress.get("masteredQuestionCount"));
        int review = asInt(progress.get("reviewQuestionCount"));
        int mockCompleted = asInt(mock.get("mockCompletedCount"));
        int mockAverageScore = asInt(mock.get("mockAverageScorePercent"));
        return UserWeeklyPrepReportDTO.builder()
                .windowStart(since)
                .windowEnd(now)
                .touchedQuestionCount(asInt(progress.get("touchedQuestionCount")))
                .masteredQuestionCount(mastered)
                .reviewQuestionCount(review)
                .noteCount(asInt(progress.get("noteCount")))
                .answerDraftCount(asInt(progress.get("answerDraftCount")))
                .mockSessionCount(asInt(mock.get("mockSessionCount")))
                .mockCompletedCount(mockCompleted)
                .mockAnsweredQuestionCount(asInt(mock.get("mockAnsweredQuestionCount")))
                .mockAverageScorePercent(mockAverageScore)
                .mockBestScorePercent(asInt(mock.get("mockBestScorePercent")))
                .mistakeReasonCounts(mistakes)
                .focusTagCounts(focusTags)
                .touchedQuestions(touchedQuestions)
                .nextActions(weeklyNextActions(mastered, review, mockCompleted, mockAverageScore, mistakes, focusTags))
                .build();
    }

    @Override
    public List<PrepTargetDTO> listPrepTargets(Long uid) {
        return prepTargetMapper.selectByUser(uid).stream().map(this::toPrepTargetDto).toList();
    }

    @Override
    @Transactional
    public PrepTargetDTO addPrepTarget(Long uid, PrepTargetCmd cmd) {
        String type = normalizeTargetType(cmd == null ? null : cmd.getTargetType());
        String value = clean(cmd == null ? null : cmd.getTargetValue());
        String priority = normalizeTargetPriority(cmd == null ? null : cmd.getPriority());
        String note = limit(clean(cmd == null ? null : cmd.getNote()), 300);
        if (value.isBlank() || value.length() > 128) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        prepTargetMapper.insertIgnore(idGen.nextId(), uid, type, value, cmd == null ? null : cmd.getInterviewDate(), priority, note);
        return prepTargetMapper.selectByUser(uid).stream()
                .filter(item -> type.equals(item.getTargetType()) && value.equals(item.getTargetValue()))
                .findFirst()
                .map(this::toPrepTargetDto)
                .orElseThrow(() -> new BizException(ErrorCode.SYSTEM_ERROR));
    }

    @Override
    @Transactional
    public Map<String, Object> deletePrepTarget(Long uid, Long targetId) {
        int deleted = prepTargetMapper.deleteByUser(targetId, uid);
        return Map.of("id", targetId, "deleted", deleted > 0);
    }

    @Override
    public List<QuestionDTO> listAdminQuestions(Integer status, int limit) {
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 30 : limit, 100));
        return toQuestionDtos(questionMapper.selectAdminRecent(status, null, null, null, null, null, null, null, 0, safeLimit), null);
    }

    @Override
    public PageResult<QuestionDTO> pageAdminQuestions(Integer status, int page, int pageSize) {
        QuestionAdminQuery query = new QuestionAdminQuery();
        query.setStatus(status);
        query.setPage(page);
        query.setPageSize(pageSize);
        return pageAdminQuestions(query);
    }

    @Override
    public PageResult<QuestionDTO> pageAdminQuestions(QuestionAdminQuery query) {
        int requestedPageSize = query == null ? 30 : query.getPageSize();
        int requestedPage = query == null ? 1 : query.getPage();
        int safePageSize = Math.max(1, Math.min(requestedPageSize <= 0 ? 30 : requestedPageSize, 100));
        int safePage = Math.max(1, requestedPage <= 0 ? 1 : requestedPage);
        Integer minQuality = clampQuality(query == null ? null : query.getMinQualityScore());
        Integer maxQuality = clampQuality(query == null ? null : query.getMaxQualityScore());
        if (minQuality != null && maxQuality != null && minQuality > maxQuality) {
            int tmp = minQuality;
            minQuality = maxQuality;
            maxQuality = tmp;
        }
        int offset = (safePage - 1) * safePageSize;
        List<QuestionDTO> items = toQuestionDtos(questionMapper.selectAdminRecent(
                query == null ? null : query.getStatus(),
                cleanToNull(query == null ? null : query.getKeyword()),
                cleanToNull(query == null ? null : query.getCompany()),
                cleanToNull(query == null ? null : query.getPosition()),
                minQuality,
                maxQuality,
                positiveLong(query == null ? null : query.getSourcePostId()),
                normalizeTaskStatus(query == null ? null : query.getTaskStatus()),
                offset,
                safePageSize), null);
        long total = questionMapper.countAdminRecent(
                query == null ? null : query.getStatus(),
                cleanToNull(query == null ? null : query.getKeyword()),
                cleanToNull(query == null ? null : query.getCompany()),
                cleanToNull(query == null ? null : query.getPosition()),
                minQuality,
                maxQuality,
                positiveLong(query == null ? null : query.getSourcePostId()),
                normalizeTaskStatus(query == null ? null : query.getTaskStatus()));
        boolean hasMore = (long) safePage * safePageSize < total;
        return PageResult.<QuestionDTO>builder()
                .items(items)
                .nextCursor(hasMore ? String.valueOf(safePage + 1) : null)
                .hasMore(hasMore)
                .total(total)
                .build();
    }

    private Integer clampQuality(Integer value) {
        if (value == null) {
            return null;
        }
        return Math.max(0, Math.min(value, 100));
    }

    private Integer normalizeTaskStatus(Integer value) {
        if (value == null) {
            return null;
        }
        return List.of(QuestionConstants.TASK_PENDING, QuestionConstants.TASK_RUNNING,
                QuestionConstants.TASK_SUCCEEDED, QuestionConstants.TASK_FAILED).contains(value) ? value : null;
    }

    private Long positiveLong(Long value) {
        return value == null || value <= 0 ? null : value;
    }

    @Override
    public Map<String, Long> questionAdminSummary() {
        Map<String, Long> summary = new HashMap<>();
        summary.put("pending", 0L);
        summary.put("approved", 0L);
        summary.put("hidden", 0L);
        for (Map<String, Object> row : questionMapper.countAdminByStatus()) {
            int status = row.get("name") instanceof Number number ? number.intValue() : -1;
            long count = asLong(row.get("count"));
            if (status == QuestionConstants.QUESTION_PENDING) summary.put("pending", count);
            if (status == QuestionConstants.QUESTION_APPROVED) summary.put("approved", count);
            if (status == QuestionConstants.QUESTION_HIDDEN) summary.put("hidden", count);
        }
        summary.put("total", summary.values().stream().mapToLong(Long::longValue).sum());
        return summary;
    }

    @Override
    @Transactional
    public QuestionDTO updateQuestionAdmin(Long questionId, QuestionAdminUpdateCmd cmd) {
        List<InterviewQuestionPO> rows = questionMapper.selectVisibleByIds(List.of(questionId), true);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        InterviewQuestionPO update = new InterviewQuestionPO();
        update.setId(questionId);
        if (cmd.getQuestionText() != null) {
            String text = clean(cmd.getQuestionText());
            if (text.length() < 4) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
            update.setQuestionText(text);
            update.setNormalizedHash(hash(normalizeQuestion(text)));
        }
        update.setAnswerHint(adminText(cmd.getAnswerHint(), 4000));
        update.setExamPoint(adminText(cmd.getExamPoint(), 255));
        update.setReferenceAnswer(adminText(cmd.getReferenceAnswer(), 4000));
        update.setSourceSnippet(adminText(cmd.getSourceSnippet(), 1000));
        update.setQualityReason(adminText(cmd.getQualityReason(), 500));
        update.setCompany(cmd.getCompany());
        update.setPosition(cmd.getPosition());
        update.setInterviewRound(cmd.getInterviewRound());
        if (cmd.getDifficulty() != null) {
            update.setDifficulty(normalizeDifficulty(cmd.getDifficulty()));
        }
        if (cmd.getStatus() != null) {
            validateQuestionStatus(cmd.getStatus());
            update.setStatus(cmd.getStatus());
        }
        InterviewQuestionPO scoreBase = rows.get(0);
        scoreBase.setQuestionText(update.getQuestionText() == null ? scoreBase.getQuestionText() : update.getQuestionText());
        scoreBase.setCompany(update.getCompany() == null ? scoreBase.getCompany() : update.getCompany());
        scoreBase.setPosition(update.getPosition() == null ? scoreBase.getPosition() : update.getPosition());
        scoreBase.setInterviewRound(update.getInterviewRound() == null ? scoreBase.getInterviewRound() : update.getInterviewRound());
        scoreBase.setExamPoint(update.getExamPoint() == null ? scoreBase.getExamPoint() : update.getExamPoint());
        scoreBase.setReferenceAnswer(update.getReferenceAnswer() == null ? scoreBase.getReferenceAnswer() : update.getReferenceAnswer());
        scoreBase.setSourceSnippet(update.getSourceSnippet() == null ? scoreBase.getSourceSnippet() : update.getSourceSnippet());
        update.setQualityScore(score(scoreBase));
        questionMapper.updateAdmin(update);
        questionSearchIndexer.indexQuestion(questionId);
        return toQuestionDtos(questionMapper.selectVisibleByIds(List.of(questionId), true), null).get(0);
    }

    @Override
    @Transactional
    public Map<String, Object> reviewQuestion(Long questionId, int status) {
        validateQuestionStatus(status);
        int updated = questionMapper.updateStatus(questionId, status);
        if (updated == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        evictQuestionDetail(questionId);
        questionSearchIndexer.indexQuestion(questionId);
        return Map.of("questionId", questionId, "status", status);
    }

    @Override
    public QuestionDuplicateGroupDTO getDuplicateGroup(Long questionId) {
        InterviewQuestionPO question = requireQuestionForDuplicateGroup(questionId);
        return duplicateGroupDto(question.getId(), question.getNormalizedHash());
    }

    @Override
    @Transactional
    public QuestionDuplicateGroupDTO setDuplicateCanonical(Long questionId, Long canonicalQuestionId) {
        InterviewQuestionPO question = requireQuestionForDuplicateGroup(questionId);
        InterviewQuestionPO canonical = requireQuestionForDuplicateGroup(canonicalQuestionId);
        if (!Objects.equals(question.getNormalizedHash(), canonical.getNormalizedHash())) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (!Objects.equals(canonical.getStatus(), QuestionConstants.QUESTION_APPROVED)) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        int appearCount = Math.max(1, questionMapper.countVisibleSourcesByHash(question.getNormalizedHash()));
        questionMapper.updateAdminCanonicalGroup(question.getNormalizedHash(), canonical.getId(), appearCount);
        questionMapper.selectAdminByHash(question.getNormalizedHash(), canonical.getId())
                .forEach(row -> questionSearchIndexer.indexQuestion(row.getId()));
        return duplicateGroupDto(question.getId(), question.getNormalizedHash());
    }

    @Override
    @Transactional
    public QuestionDuplicateGroupDTO mergeDuplicateCandidate(Long questionId, Long candidateQuestionId) {
        InterviewQuestionPO question = requireQuestionForDuplicateGroup(questionId);
        InterviewQuestionPO candidate = requireQuestionForDuplicateGroup(candidateQuestionId);
        if (Objects.equals(question.getId(), candidate.getId())) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        if (!Objects.equals(candidate.getStatus(), QuestionConstants.QUESTION_APPROVED)
                && !Objects.equals(candidate.getStatus(), QuestionConstants.QUESTION_PENDING)) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        int similarityScore = semanticSimilarityScore(question, candidate);
        if (similarityScore < 72) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Long canonicalId = canonicalIdFor(question);
        if (canonicalId == null) {
            canonicalId = question.getId();
        }
        questionMapper.updateCanonicalId(candidate.getId(), canonicalId);
        int appearCount = Math.max(1, questionMapper.countVisibleSourcesByHash(question.getNormalizedHash()))
                + Math.max(1, questionMapper.countVisibleSourcesByHash(candidate.getNormalizedHash()));
        questionMapper.updateAdminCanonicalGroup(question.getNormalizedHash(), canonicalId, appearCount);
        questionSearchIndexer.indexQuestion(question.getId());
        questionSearchIndexer.indexQuestion(candidate.getId());
        return duplicateGroupDto(question.getId(), question.getNormalizedHash());
    }

    @Override
    @Transactional
    public QuestionDuplicateGroupDTO hideDuplicateQuestions(Long questionId, List<Long> duplicateQuestionIds) {
        InterviewQuestionPO question = requireQuestionForDuplicateGroup(questionId);
        List<Long> ids = duplicateQuestionIds == null ? List.of() : duplicateQuestionIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0 && !Objects.equals(id, questionId))
                .distinct()
                .limit(50)
                .toList();
        if (ids.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        Map<Long, InterviewQuestionPO> groupById = questionMapper.selectAdminByHash(question.getNormalizedHash(), canonicalIdFor(question)).stream()
                .collect(Collectors.toMap(InterviewQuestionPO::getId, item -> item, (a, b) -> a));
        for (Long id : ids) {
            if (!groupById.containsKey(id)) {
                throw new BizException(ErrorCode.PARAM_ERROR);
            }
            reviewQuestion(id, QuestionConstants.QUESTION_HIDDEN);
        }
        refreshCanonicalGroup(question.getNormalizedHash());
        return duplicateGroupDto(question.getId(), question.getNormalizedHash());
    }

    @Override
    public List<CompanyAliasDTO> listCompanyAliases(String keyword, int limit) {
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 100));
        return companyAliasMapper.listAdmin(clean(keyword), safeLimit).stream()
                .map(this::toCompanyAliasDto)
                .toList();
    }

    @Override
    public List<CompanyAliasCandidateDTO> listCompanyAliasCandidates(int limit) {
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 20 : limit, 50));
        Map<String, CompanyNameStat> stats = aggregateCompanyNameStats(200);
        if (stats.size() < 2) {
            return List.of();
        }
        Set<String> existingAliases = companyAliasMapper.listAdmin(null, 500).stream()
                .map(CompanyAliasPO::getAlias)
                .map(this::aliasCandidateKey)
                .filter(key -> !key.isBlank())
                .collect(Collectors.toSet());
        List<CompanyNameStat> companies = stats.values().stream()
                .sorted(Comparator.comparingLong(CompanyNameStat::totalCount).reversed()
                        .thenComparing(CompanyNameStat::name))
                .toList();
        Map<String, CompanyAliasCandidateDTO> candidates = new HashMap<>();
        for (int i = 0; i < companies.size(); i++) {
            for (int j = i + 1; j < companies.size(); j++) {
                AliasMatch match = matchCompanyAlias(companies.get(i).name(), companies.get(j).name());
                if (match == null) {
                    continue;
                }
                CompanyNameStat alias = stats.get(aliasCandidateKey(match.alias()));
                if (alias == null || existingAliases.contains(aliasCandidateKey(alias.name()))) {
                    continue;
                }
                String key = aliasCandidateKey(match.canonicalCompany()) + "->" + aliasCandidateKey(match.alias());
                CompanyNameStat canonical = stats.get(aliasCandidateKey(match.canonicalCompany()));
                long canonicalCount = canonical == null ? 0L : canonical.totalCount();
                candidates.putIfAbsent(key, CompanyAliasCandidateDTO.builder()
                        .canonicalCompany(match.canonicalCompany())
                        .alias(alias.name())
                        .canonicalSampleCount(canonicalCount)
                        .aliasSampleCount(alias.totalCount())
                        .questionSampleCount(alias.questionCount())
                        .postSampleCount(alias.postCount())
                        .totalSampleCount(canonicalCount + alias.totalCount())
                        .reason(match.reason())
                        .sampleCompanies(List.of(match.canonicalCompany(), alias.name()))
                        .build());
            }
        }
        return candidates.values().stream()
                .sorted(Comparator.comparingLong((CompanyAliasCandidateDTO item) -> nullToZero(item.getTotalSampleCount())).reversed()
                        .thenComparing(CompanyAliasCandidateDTO::getCanonicalCompany)
                        .thenComparing(CompanyAliasCandidateDTO::getAlias))
                .limit(safeLimit)
                .toList();
    }

    @Override
    @Transactional
    public CompanyAliasDTO saveCompanyAlias(Long id, CompanyAliasCmd cmd) {
        String canonical = clean(cmd == null ? null : cmd.getCanonicalCompany());
        String alias = clean(cmd == null ? null : cmd.getAlias());
        if (canonical.length() < 2 || canonical.length() > 128 || alias.length() < 2 || alias.length() > 128) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        int status = normalizeAliasStatus(cmd == null ? null : cmd.getStatus());
        CompanyAliasPO old = id == null ? null : companyAliasMapper.selectById(id);
        CompanyAliasPO conflict = companyAliasMapper.findByAlias(alias);
        if (conflict != null && !Objects.equals(conflict.getId(), id)) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION);
        }
        CompanyAliasPO po = new CompanyAliasPO();
        po.setId(id == null ? idGen.nextId() : id);
        po.setCanonicalCompany(canonical);
        po.setAlias(alias);
        po.setStatus(status);
        if (id == null) {
            companyAliasMapper.insert(po);
        } else {
            int updated = companyAliasMapper.updateById(po);
            if (updated == 0) {
                throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
            }
        }
        evictQuestionCachesByCompany(canonical);
        if (old != null) {
            evictQuestionCachesByCompany(old.getCanonicalCompany());
        }
        return toCompanyAliasDto(companyAliasMapper.selectById(po.getId()));
    }

    @Override
    @Transactional
    public Map<String, Object> updateCompanyAliasStatus(Long id, int status) {
        int normalized = normalizeAliasStatus(status);
        CompanyAliasPO old = companyAliasMapper.selectById(id);
        if (old == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        companyAliasMapper.updateStatus(id, normalized);
        evictQuestionCachesByCompany(old.getCanonicalCompany());
        return Map.of("id", id, "status", normalized);
    }

    private CompanyPrepDTO loadCompanyPrep(String canonical, Long viewerUid) {
        List<String> aliases = companyAliasMapper.listAliases(canonical).stream()
                .map(CompanyAliasPO::getAlias)
                .filter(alias -> alias != null && !alias.isBlank())
                .distinct()
                .toList();
        List<InterviewQuestionPO> companyQuestionPool = questionMapper.selectTopByCompany(canonical, 20);
        List<QuestionDTO> topQuestions = toQuestionDtos(companyQuestionPool.stream().limit(10).toList(), viewerUid);
        List<QuestionDTO> recommendedQuestions = viewerUid == null ? List.of() : companyRecommendedQuestions(companyQuestionPool, viewerUid);
        List<PostBriefDTO> recentPosts = recentCompanyPosts(canonical);
        Map<String, Object> questionSummary = Objects.requireNonNullElse(questionMapper.summarizeCompanyQuestions(canonical), Map.of());
        Map<String, Object> postSummary = Objects.requireNonNullElse(postMapper.summarizeInterviewPostsByCompany(canonical), Map.of());
        List<CompanyPrepDTO.NameCountDTO> hotPositions = toNameCounts(questionMapper.countPositionsByCompany(canonical, 8));
        List<CompanyPrepDTO.NameCountDTO> topTags = toNameCounts(questionTagMapper.countTagsByCompany(canonical, 10));
        List<CompanyPrepDTO.NameCountDTO> trend30Days = toNameCounts(questionMapper.countByCompanySince(canonical, LocalDateTime.now().minusDays(30)));
        List<CompanyPrepDTO.NameCountDTO> trend90Days = toNameCounts(questionMapper.countByCompanySince(canonical, LocalDateTime.now().minusDays(90)));
        List<CompanyPrepDTO.NameCountDTO> interviewResultDistribution = toInterviewResultCounts(
                postMapper.countInterviewResultsByCompany(canonical, null));
        List<CompanyPrepDTO.NameCountDTO> recentResultDistribution = toInterviewResultCounts(
                postMapper.countInterviewResultsByCompany(canonical, LocalDateTime.now().minusDays(30)));
        CompanyPrepDTO.UserPrepSummaryDTO progress = viewerUid == null ? null : userPrepSummary(progressMapper.countByCompany(viewerUid, canonical));
        boolean targetAdded = viewerUid != null && listPrepTargets(viewerUid).stream()
                .anyMatch(target -> "company".equals(target.getTargetType()) && canonical.equals(target.getTargetValue()));
        List<CompanyPrepDTO.ChecklistItemDTO> checklist = prepAssembler.companyPrepChecklist(canonical, viewerUid, targetAdded, topQuestions, recentPosts, hotPositions, topTags, progress);
        return CompanyPrepDTO.builder()
                .company(canonical)
                .aliases(aliases)
                .relatedPositionCount((int) questionMapper.countDistinctPositionsByCompany(canonical))
                .recentPosts(recentPosts)
                .topQuestions(topQuestions)
                .recommendedQuestions(recommendedQuestions)
                .topTags(topTags)
                .hotPositions(hotPositions)
                .trend30Days(trend30Days)
                .trend90Days(trend90Days)
                .interviewResultDistribution(interviewResultDistribution)
                .recentResultDistribution(recentResultDistribution)
                .questionSampleCount(asLong(questionSummary.get("sampleCount")))
                .postSampleCount(asLong(postSummary.get("sampleCount")))
                .resultSampleCount(sumCounts(interviewResultDistribution))
                .recentResultSampleCount(sumCounts(recentResultDistribution))
                .dataUpdatedAt(maxTime(asLocalDateTime(questionSummary.get("updatedAt")), asLocalDateTime(postSummary.get("updatedAt"))))
                .myProgress(progress)
                .prepScore(prepAssembler.prepScore(checklist))
                .checklist(checklist)
                .nextActions(prepAssembler.nextActions(checklist))
                .build();
    }

    private QuestionDetailDTO loadQuestionDetail(Long questionId, Long viewerUid, boolean admin) {
        List<InterviewQuestionPO> visible = questionMapper.selectVisibleByIds(List.of(questionId), admin);
        if (visible.isEmpty()) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        InterviewQuestionPO question = visible.get(0);
        List<PostBriefDTO> sourcePosts = postFacade.batchGetPosts(List.of(question.getSourcePostId())).values().stream().toList();
        List<QuestionDTO> related = toQuestionDtos(questionMapper.selectRelated(question.getId(), question.getCanonicalId(),
                question.getCompany(), question.getPosition(), 8), viewerUid);
        return QuestionDetailDTO.builder()
                .question(toQuestionDtos(List.of(question), viewerUid).get(0))
                .sourcePosts(sourcePosts)
                .relatedQuestions(related)
                .build();
    }

    @Transactional
    public void hidePostQuestions(Long postId) {
        List<InterviewQuestionPO> questions = questionMapper.selectByPostId(postId, true);
        List<Long> ids = questions.stream()
                .map(InterviewQuestionPO::getId)
                .toList();
        Set<String> changedHashes = questions.stream()
                .map(InterviewQuestionPO::getNormalizedHash)
                .filter(hash -> hash != null && !hash.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> affectedCompanies = questions.stream()
                .map(InterviewQuestionPO::getCompany)
                .filter(company -> company != null && !company.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        questionMapper.hideByPostId(postId);
        ids.forEach(questionSearchIndexer::indexQuestion);
        changedHashes.forEach(this::refreshCanonicalGroup);
        affectedCompanies.forEach(this::evictQuestionCachesByCompany);
    }

    public void evictQuestionCachesByCompany(String company) {
        if (company == null || company.isBlank()) {
            return;
        }
        companyPrepCache.evict(CacheKeyBuilder.companyPrep(company));
    }

    public void evictQuestionDetail(Long questionId) {
        // Detail responses contain viewer-specific progress, so they are loaded fresh.
    }

    public void evictQuestionCachesForPost(PostDTO post) {
        if (post == null || post.getExtJson() == null || post.getExtJson().isBlank()) {
            return;
        }
        String company = extValue(post.getExtJson(), "company");
        evictQuestionCachesByCompany(company);
    }

    private List<ExtractedQuestion> extractQuestions(PostDTO post) {
        return questionExtractor.extract(post).stream()
                .filter(item -> clean(item.getQuestionText()).length() >= 4)
                .collect(Collectors.collectingAndThen(Collectors.toMap(
                        item -> hash(normalizeQuestion(item.getQuestionText())),
                        item -> item,
                        (a, b) -> a,
                        java.util.LinkedHashMap::new
                ), map -> map.values().stream().limit(20).toList()));
    }

    private Integer replacePostQuestions(PostDTO post, List<ExtractedQuestion> extracted) {
        List<InterviewQuestionPO> oldQuestions = questionMapper.selectByPostId(post.getId(), true);
        List<Long> oldQuestionIds = oldQuestions.stream()
                .map(InterviewQuestionPO::getId)
                .toList();
        Set<String> changedHashes = oldQuestions.stream()
                .map(InterviewQuestionPO::getNormalizedHash)
                .filter(hash -> hash != null && !hash.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> affectedCompanies = oldQuestions.stream()
                .map(InterviewQuestionPO::getCompany)
                .filter(company -> company != null && !company.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        questionTagMapper.deleteByPostId(post.getId());
        questionMapper.delete(new LambdaQueryWrapper<InterviewQuestionPO>()
                .eq(InterviewQuestionPO::getSourcePostId, post.getId()));
        oldQuestionIds.forEach(questionSearchIndexer::deleteQuestion);
        int count = 0;
        for (ExtractedQuestion item : extracted) {
            Long questionId = idGen.nextId();
            String normalizedHash = hash(normalizeQuestion(item.getQuestionText()));
            InterviewQuestionPO po = new InterviewQuestionPO();
            po.setId(questionId);
            po.setQuestionText(clean(item.getQuestionText()));
            po.setNormalizedHash(normalizedHash);
            po.setCanonicalId(questionMapper.selectCanonicalIdByHash(normalizedHash));
            po.setAnswerHint(cleanToNull(item.getAnswerHint()));
            po.setExamPoint(limit(cleanToNull(item.getExamPoint()), 255));
            po.setReferenceAnswer(limit(cleanToNull(item.getReferenceAnswer()), 4000));
            po.setSourceSnippet(limit(cleanToNull(item.getSourceSnippet()), 1000));
            po.setQualityReason(limit(cleanToNull(item.getQualityReason()), 500));
            po.setCompany(firstNonBlank(item.getCompany(), extValue(post.getExtJson(), "company")));
            po.setPosition(firstNonBlank(item.getPosition(), extValue(post.getExtJson(), "position")));
            po.setInterviewRound(cleanToNull(item.getInterviewRound()));
            po.setDifficulty(firstNonBlank(item.getDifficulty(), "medium"));
            po.setConfidence(item.getConfidence() == null ? new BigDecimal("0.5000") : item.getConfidence());
            po.setSourcePostId(post.getId());
            po.setSourceAuthorUid(post.getAuthorId());
            po.setAppearCount(1);
            po.setQualityScore(score(po));
            AutoReviewDecision decision = autoReviewDecision(po);
            po.setStatus(decision.status());
            po.setQualityReason(appendReviewReason(po.getQualityReason(), decision.reason()));
            questionMapper.insert(po);
            changedHashes.add(normalizedHash);
            if (po.getCompany() != null && !po.getCompany().isBlank()) {
                affectedCompanies.add(po.getCompany());
            }
            for (Long tagId : safeTagIds(item.getTagIds(), post)) {
                questionTagMapper.insertIgnore(idGen.nextId(), questionId, tagId);
            }
            count++;
        }
        changedHashes.forEach(this::refreshCanonicalGroup);
        affectedCompanies.forEach(this::evictQuestionCachesByCompany);
        return count;
    }

    private AutoReviewDecision autoReviewDecision(InterviewQuestionPO po) {
        List<String> reasons = new java.util.ArrayList<>();
        String qualityReason = clean(po.getQualityReason());
        if (qualityReason.contains("规则提取")) {
            reasons.add("规则提取");
        }
        if (po.getConfidence() == null || po.getConfidence().compareTo(new BigDecimal("0.7500")) < 0) {
            reasons.add("低置信度");
        }
        if (po.getQualityScore() == null || po.getQualityScore() < 75) {
            reasons.add("质量分偏低");
        }
        if (po.getReferenceAnswer() == null || po.getReferenceAnswer().length() < 40) {
            reasons.add("缺参考答案");
        }
        if (po.getSourceSnippet() == null || po.getSourceSnippet().isBlank()) {
            reasons.add("缺来源片段");
        }
        if (reasons.isEmpty()) {
            return new AutoReviewDecision(QuestionConstants.QUESTION_APPROVED, "自动审核：高质量结构化题，已通过");
        }
        return new AutoReviewDecision(QuestionConstants.QUESTION_PENDING,
                "自动审核：" + String.join("/", reasons) + "，进入待审核");
    }

    private String appendReviewReason(String current, String reason) {
        String base = clean(current);
        String merged = base.isBlank() ? reason : base + "；" + reason;
        return limit(merged, 500);
    }

    private record AutoReviewDecision(int status, String reason) {
    }

    private void refreshCanonicalGroup(String normalizedHash) {
        String hash = clean(normalizedHash);
        if (hash.isBlank()) {
            return;
        }
        Long canonicalId = questionMapper.selectCanonicalIdByHash(hash);
        if (canonicalId == null) {
            return;
        }
        int appearCount = Math.max(1, questionMapper.countVisibleSourcesByHash(hash));
        questionMapper.updateCanonicalGroup(hash, canonicalId, appearCount);
        questionMapper.selectVisibleByHash(hash).forEach(row -> questionSearchIndexer.indexQuestion(row.getId()));
    }

    private java.util.Optional<QuestionSearchResult> searchQuestionsByEs(QuestionQuery query, int offset, int limit) {
        return questionSearchIndexer.search(query, offset, limit)
                .filter(hits -> !hits.isEmpty())
                .map(hits -> {
                    List<Long> ids = hits.stream()
                            .map(QuestionSearchIndexer.QuestionSearchHit::questionId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList();
                    Map<Long, QuestionSearchIndexer.QuestionSearchHit> highlights = hits.stream()
                            .filter(hit -> hit.questionId() != null)
                            .collect(Collectors.toMap(QuestionSearchIndexer.QuestionSearchHit::questionId, hit -> hit, (a, b) -> a));
                    Map<Long, InterviewQuestionPO> byId = questionMapper.selectVisibleByIds(ids, false).stream()
                            .collect(Collectors.toMap(InterviewQuestionPO::getId, row -> row, (a, b) -> a));
                    List<InterviewQuestionPO> rows = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
                    return new QuestionSearchResult(rows, highlights);
                });
    }

    private AiExtractTaskPO newTask(Long postId) {
        AiExtractTaskPO task = new AiExtractTaskPO();
        task.setId(idGen.nextId());
        task.setPostId(postId);
        task.setTaskType(QuestionConstants.TASK_TYPE_QUESTION_EXTRACT);
        task.setTaskStatus(QuestionConstants.TASK_PENDING);
        task.setRetryCount(0);
        task.setQuestionCount(0);
        return task;
    }

    private void succeed(AiExtractTaskPO task, int questionCount) {
        task.setTaskStatus(QuestionConstants.TASK_SUCCEEDED);
        task.setQuestionCount(questionCount);
        task.setErrorMessage(null);
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private void fail(AiExtractTaskPO task, String message) {
        task.setTaskStatus(QuestionConstants.TASK_FAILED);
        task.setErrorMessage(limit(message, 1000));
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private void publishExtractionFinished(AiExtractTaskPO task, PostDTO post, boolean success, int questionCount, String errorMessage) {
        if (task == null || post == null || post.getAuthorId() == null) {
            return;
        }
        events.publishEvent(QuestionExtractionFinishedEvent.builder()
                .taskId(task.getId())
                .postId(post.getId())
                .postAuthorUid(post.getAuthorId())
                .postTitle(post.getTitle())
                .success(success)
                .questionCount(Math.max(0, questionCount))
                .errorMessage(success ? null : limit(errorMessage, 300))
                .timestamp(Instant.now().toEpochMilli())
                .build());
    }

    private List<QuestionDTO> toQuestionDtos(List<InterviewQuestionPO> rows, Long viewerUid) {
        return toQuestionDtos(rows, viewerUid, Map.of());
    }

    private List<QuestionDTO> toQuestionDtos(List<InterviewQuestionPO> rows,
                                             Long viewerUid,
                                             Map<Long, QuestionSearchIndexer.QuestionSearchHit> highlights) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(InterviewQuestionPO::getId).toList();
        Map<Long, List<QuestionTagDTO>> tags = tagsByQuestionIds(ids);
        Map<Long, UserQuestionProgressPO> progress = progressByQuestionIds(viewerUid, ids);
        Map<Long, QuestionSearchIndexer.QuestionSearchHit> highlightMap = highlights == null ? Map.of() : highlights;
        return rows.stream().map(row -> {
            UserQuestionProgressPO p = progress.get(row.getId());
            QuestionSearchIndexer.QuestionSearchHit hit = highlightMap.get(row.getId());
            return QuestionDTO.builder()
                    .id(row.getId())
                    .canonicalId(row.getCanonicalId())
                    .questionText(row.getQuestionText())
                    .highlightQuestionText(hit == null ? null : hit.highlightQuestionText())
                    .answerHint(row.getAnswerHint())
                    .highlightAnswerHint(hit == null ? null : hit.highlightAnswerHint())
                    .examPoint(row.getExamPoint())
                    .highlightExamPoint(hit == null ? null : hit.highlightExamPoint())
                    .referenceAnswer(row.getReferenceAnswer())
                    .sourceSnippet(row.getSourceSnippet())
                    .qualityReason(row.getQualityReason())
                    .company(row.getCompany())
                    .position(row.getPosition())
                    .interviewRound(row.getInterviewRound())
                    .difficulty(row.getDifficulty())
                    .confidence(row.getConfidence())
                    .sourcePostId(row.getSourcePostId())
                    .sourceAuthorUid(row.getSourceAuthorUid())
                    .status(row.getStatus())
                    .appearCount(row.getAppearCount())
                    .qualityScore(row.getQualityScore())
                    .tags(tags.getOrDefault(row.getId(), List.of()))
                    .favorite(p != null && Objects.equals(p.getFavorite(), 1))
                    .progressStatus(p == null ? null : p.getProgressStatus())
                    .note(p == null ? null : p.getNote())
                    .mistakeReason(p == null ? null : p.getMistakeReason())
                    .answerDraft(p == null ? null : p.getAnswerDraft())
                    .starStory(p == null ? null : p.getStarStory())
                    .nextReviewAt(p == null ? null : p.getNextReviewAt())
                    .lastReviewedAt(p == null ? null : p.getLastReviewedAt())
                    .reviewCount(p == null ? 0 : safeInt(p.getReviewCount(), 0))
                    .reviewIntervalDays(p == null ? 1 : safeInt(p.getReviewIntervalDays(), 1))
                    .sourcePostCount(row.getAppearCount())
                    .createTime(row.getCreateTime())
                    .updateTime(row.getUpdateTime())
                    .build();
        }).toList();
    }

    private record QuestionSearchResult(List<InterviewQuestionPO> rows,
                                        Map<Long, QuestionSearchIndexer.QuestionSearchHit> highlights) {
    }

    private Map<Long, List<QuestionTagDTO>> tagsByQuestionIds(Collection<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Map.of();
        }
        return questionTagMapper.selectTagsByQuestionIds(questionIds).stream()
                .collect(Collectors.groupingBy(PostTagView::getPostId,
                        Collectors.mapping(tag -> QuestionTagDTO.builder()
                                .id(tag.getId())
                                .name(tag.getTagName())
                                .tagType(tag.getTagType())
                                .build(), Collectors.toList())));
    }

    private Map<Long, UserQuestionProgressPO> progressByQuestionIds(Long viewerUid, Collection<Long> questionIds) {
        if (viewerUid == null || questionIds == null || questionIds.isEmpty()) {
            return Map.of();
        }
        return progressMapper.selectByUserAndQuestions(viewerUid, questionIds).stream()
                .collect(Collectors.toMap(UserQuestionProgressPO::getQuestionId, p -> p, (a, b) -> a));
    }

    private CompanyPrepDTO.UserPrepSummaryDTO userPrepSummary(Map<String, Object> progress) {
        Map<String, Object> counts = progress == null ? Map.of() : progress;
        return CompanyPrepDTO.UserPrepSummaryDTO.builder()
                .favoriteCount(asLong(counts.get("favoriteCount")))
                .learningCount(asLong(counts.get("learningCount")))
                .masteredCount(asLong(counts.get("masteredCount")))
                .reviewCount(asLong(counts.get("reviewCount")))
                .build();
    }

    private List<QuestionDTO> questionsFromProgress(List<UserQuestionProgressPO> progress, Long viewerUid) {
        if (progress == null || progress.isEmpty()) {
            return List.of();
        }
        List<Long> ids = progress.stream().map(UserQuestionProgressPO::getQuestionId).toList();
        Map<Long, InterviewQuestionPO> byId = questionMapper.selectVisibleByIds(ids, false).stream()
                .collect(Collectors.toMap(InterviewQuestionPO::getId, row -> row, (a, b) -> a));
        return toQuestionDtos(ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList(), viewerUid);
    }

    private List<QuestionDTO> recommendedForTargets(List<PrepTargetDTO> targets, Long viewerUid) {
        if (targets == null || targets.isEmpty()) {
            return toQuestionDtos(questionMapper.selectRecommended(10), viewerUid);
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (PrepTargetDTO target : targets) {
            questionIdsForTarget(target, 6).forEach(ids::add);
            if (ids.size() >= 10) {
                break;
            }
        }
        if (ids.isEmpty()) {
            return toQuestionDtos(questionMapper.selectRecommended(10), viewerUid);
        }
        Map<Long, InterviewQuestionPO> byId = questionMapper.selectVisibleByIds(ids, false).stream()
                .collect(Collectors.toMap(InterviewQuestionPO::getId, row -> row, (a, b) -> a));
        return toQuestionDtos(ids.stream().map(byId::get).filter(Objects::nonNull).limit(10).toList(), viewerUid);
    }

    private List<QuestionDTO> companyRecommendedQuestions(List<InterviewQuestionPO> companyQuestionPool, Long viewerUid) {
        if (viewerUid == null || companyQuestionPool == null || companyQuestionPool.isEmpty()) {
            return List.of();
        }
        List<Long> ids = companyQuestionPool.stream()
                .map(InterviewQuestionPO::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, UserQuestionProgressPO> progressByQuestion = progressMapper.selectByUserAndQuestions(viewerUid, ids).stream()
                .collect(Collectors.toMap(UserQuestionProgressPO::getQuestionId, row -> row, (a, b) -> a));
        List<InterviewQuestionPO> personalized = companyQuestionPool.stream()
                .filter(question -> {
                    UserQuestionProgressPO progress = progressByQuestion.get(question.getId());
                    return progress == null || !"mastered".equals(progress.getProgressStatus());
                })
                .sorted(Comparator.comparingInt((InterviewQuestionPO question) -> companyRecommendationRank(progressByQuestion.get(question.getId())))
                        .thenComparing(InterviewQuestionPO::getAppearCount, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(InterviewQuestionPO::getQualityScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(InterviewQuestionPO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .toList();
        return toQuestionDtos(personalized, viewerUid);
    }

    private int companyRecommendationRank(UserQuestionProgressPO progress) {
        if (progress == null || progress.getProgressStatus() == null || progress.getProgressStatus().isBlank()) {
            return 2;
        }
        return switch (progress.getProgressStatus()) {
            case "review" -> 0;
            case "learning" -> 1;
            case "todo" -> 2;
            default -> 3;
        };
    }

    private List<UserPrepOverviewDTO.TargetPrepSummaryDTO> targetSummaries(List<PrepTargetDTO> targets, Long viewerUid) {
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }
        return targets.stream()
                .map(target -> targetSummary(target, viewerUid))
                .toList();
    }

    private List<UserPrepOverviewDTO.MistakeReasonCountDTO> mistakeReasonCounts(Long uid) {
        List<Map<String, Object>> rows = progressMapper.countMistakeReasons(uid);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> UserPrepOverviewDTO.MistakeReasonCountDTO.builder()
                        .reason(String.valueOf(row.getOrDefault("name", "")))
                        .count(asLong(row.get("count")))
                        .build())
                .toList();
    }

    private List<UserPrepOverviewDTO.FocusTagCountDTO> focusTagCounts(Long uid) {
        LocalDateTime staleLearningBefore = LocalDateTime.now().minusDays(2);
        List<Map<String, Object>> rows = progressMapper.countWeaknessTags(uid, staleLearningBefore, 8);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> UserPrepOverviewDTO.FocusTagCountDTO.builder()
                        .name(String.valueOf(row.getOrDefault("name", "")))
                        .count(asLong(row.get("count")))
                        .build())
                .toList();
    }

    private UserPrepOverviewDTO.ReviewPlanDTO reviewPlan(Long uid) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleLearningBefore = now.minusDays(2);
        LocalDateTime weekStart = now.minusDays(7);
        List<UserQuestionProgressPO> today = progressMapper.selectReviewCandidates(uid, staleLearningBefore, 8);
        List<UserQuestionProgressPO> weekTouched = progressMapper.selectUpdatedSince(uid, weekStart, 8);
        return UserPrepOverviewDTO.ReviewPlanDTO.builder()
                .todayCount(progressMapper.countReviewCandidates(uid, staleLearningBefore))
                .weekTouchedCount(progressMapper.countUpdatedSince(uid, weekStart))
                .todayQuestions(questionsFromProgress(today, uid))
                .weekTouchedQuestions(questionsFromProgress(weekTouched, uid))
                .build();
    }

    private List<String> weeklyNextActions(int mastered,
                                           int review,
                                           int mockCompleted,
                                           int mockAverageScore,
                                           List<UserPrepOverviewDTO.MistakeReasonCountDTO> mistakes,
                                           List<UserPrepOverviewDTO.FocusTagCountDTO> focusTags) {
        List<String> actions = new java.util.ArrayList<>();
        if (review > 0) {
            actions.add("优先清理 " + review + " 道待复习题，先把本周遗留问题收口");
        }
        if (focusTags != null && !focusTags.isEmpty()) {
            actions.add("围绕 " + focusTags.get(0).getName() + " 做一轮专项模拟面试");
        }
        if (mistakes != null && !mistakes.isEmpty()) {
            actions.add("针对 " + mistakeReasonLabel(mistakes.get(0).getReason()) + " 错因补一张回答卡片");
        }
        if (mockCompleted <= 0) {
            actions.add("安排至少 1 场模拟面试，补齐表达和限时输出反馈");
        } else if (mockAverageScore > 0 && mockAverageScore < 70) {
            actions.add("复盘本周模拟面试低分题，把 2 分以下答案加入待复习");
        }
        if (mastered <= 0) {
            actions.add("本周至少标记 3 道已掌握题，形成可见进度");
        }
        return actions.stream().distinct().limit(5).toList();
    }

    private String mistakeReasonLabel(String reason) {
        return switch (clean(reason)) {
            case "concept" -> "概念不熟";
            case "project" -> "项目表达弱";
            case "memory" -> "需要记忆";
            case "expression" -> "表达不清";
            case "careless" -> "粗心失误";
            case "other" -> "其他错因";
            default -> clean(reason).isBlank() ? "未分类" : clean(reason);
        };
    }

    private UserPrepOverviewDTO.TargetPrepSummaryDTO targetSummary(PrepTargetDTO target, Long viewerUid) {
        List<Long> ids = questionIdsForTarget(target, 8);
        List<QuestionDTO> questions = questionsByIds(ids, viewerUid, 4);
        TargetFilter filter = targetFilter(target);
        int questionCount = questionMapper.countPublicByTarget(filter.company(), filter.position(), filter.tagName());
        Map<String, Object> progress = viewerUid == null ? Map.of()
                : progressMapper.countByTarget(viewerUid, filter.company(), filter.position(), filter.tagName());
        return UserPrepOverviewDTO.TargetPrepSummaryDTO.builder()
                .target(target)
                .questionCount(questionCount)
                .favoriteCount(asLong(progress.get("favoriteCount")))
                .learningCount(asLong(progress.get("learningCount")))
                .masteredCount(asLong(progress.get("masteredCount")))
                .reviewCount(asLong(progress.get("reviewCount")))
                .recommendedQuestions(questions)
                .build();
    }

    private TargetFilter targetFilter(PrepTargetDTO target) {
        String type = target == null ? "" : clean(target.getTargetType());
        String value = target == null ? "" : clean(target.getTargetValue());
        if ("company".equals(type)) {
            return new TargetFilter(value, null, null);
        }
        if ("position".equals(type)) {
            return new TargetFilter(null, value, null);
        }
        if ("tag".equals(type)) {
            return new TargetFilter(null, null, value);
        }
        return new TargetFilter(null, null, null);
    }

    private List<Long> questionIdsForTarget(PrepTargetDTO target, int limit) {
        if (target == null) {
            return List.of();
        }
        String type = target.getTargetType();
        String value = clean(target.getTargetValue());
        if (value.isBlank()) {
            return List.of();
        }
        String company = "company".equals(type) ? value : null;
        String position = "position".equals(type) ? value : null;
        if ("tag".equals(type)) {
            return questionMapper.selectTopByTagName(value, Math.max(1, Math.min(limit, 20)))
                    .stream()
                    .map(InterviewQuestionPO::getId)
                    .toList();
        }
        return questionMapper.searchPublic(null, clean(company), clean(position), null,
                null, null, null, null, "hot", null, null, null, false, false, false, false, 0, Math.max(1, Math.min(limit, 20)))
                .stream()
                .map(InterviewQuestionPO::getId)
                .toList();
    }

    private record TargetFilter(String company, String position, String tagName) {
    }

    private record ReviewSchedule(LocalDateTime nextReviewAt,
                                  LocalDateTime lastReviewedAt,
                                  int reviewCountDelta,
                                  int reviewIntervalDays) {
    }

    private List<QuestionDTO> questionsByIds(Collection<Long> ids, Long viewerUid, int limit) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<Long, InterviewQuestionPO> byId = questionMapper.selectVisibleByIds(ids, false).stream()
                .collect(Collectors.toMap(InterviewQuestionPO::getId, row -> row, (a, b) -> a));
        return toQuestionDtos(ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .limit(Math.max(1, limit))
                .toList(), viewerUid);
    }

    private PrepTargetDTO toPrepTargetDto(UserPrepTargetPO po) {
        return PrepTargetDTO.builder()
                .id(po.getId())
                .uid(po.getUid())
                .targetType(po.getTargetType())
                .targetValue(po.getTargetValue())
                .interviewDate(po.getInterviewDate())
                .priority(po.getPriority())
                .note(po.getNote())
                .createTime(po.getCreateTime())
                .build();
    }

    private CompanyAliasDTO toCompanyAliasDto(CompanyAliasPO po) {
        return CompanyAliasDTO.builder()
                .id(po.getId())
                .canonicalCompany(po.getCanonicalCompany())
                .alias(po.getAlias())
                .status(po.getStatus())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    private InterviewQuestionPO requireQuestionForDuplicateGroup(Long questionId) {
        if (questionId == null || questionId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        List<InterviewQuestionPO> rows = questionMapper.selectVisibleByIds(List.of(questionId), true);
        if (rows.isEmpty() || clean(rows.get(0).getNormalizedHash()).isBlank()) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return rows.get(0);
    }

    private QuestionDuplicateGroupDTO duplicateGroupDto(Long questionId, String normalizedHash) {
        String hash = clean(normalizedHash);
        if (hash.isBlank()) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Long canonicalId = questionMapper.selectCanonicalIdByHash(hash);
        List<InterviewQuestionPO> rows = questionMapper.selectAdminByHash(hash, canonicalId);
        int sourcePostCount = (int) rows.stream()
                .map(InterviewQuestionPO::getSourcePostId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        return QuestionDuplicateGroupDTO.builder()
                .questionId(questionId)
                .canonicalId(canonicalId)
                .normalizedHash(hash)
                .sourcePostCount(sourcePostCount)
                .questionCount(rows.size())
                .questions(toQuestionDtos(rows, null))
                .semanticCandidates(semanticDuplicateCandidates(questionId, rows, hash))
                .build();
    }

    private List<QuestionDuplicateCandidateDTO> semanticDuplicateCandidates(Long questionId,
                                                                            List<InterviewQuestionPO> groupRows,
                                                                            String normalizedHash) {
        InterviewQuestionPO base = groupRows.stream()
                .filter(row -> Objects.equals(row.getId(), questionId))
                .findFirst()
                .orElseGet(() -> groupRows.isEmpty() ? null : groupRows.get(0));
        if (base == null) {
            return List.of();
        }
        Set<Long> existingIds = groupRows.stream()
                .map(InterviewQuestionPO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<QuestionDuplicateCandidateDTO> candidates = questionMapper.selectSemanticDuplicateCandidates(base.getId(), normalizedHash, clean(base.getCompany()), clean(base.getPosition()), 80)
                .stream()
                .filter(candidate -> !existingIds.contains(candidate.getId()))
                .map(candidate -> QuestionDuplicateCandidateDTO.builder()
                        .question(toQuestionDtos(List.of(candidate), null).get(0))
                        .similarityScore(semanticSimilarityScore(base, candidate))
                        .reason(semanticSimilarityReason(base, candidate))
                        .build())
                .filter(candidate -> candidate.getSimilarityScore() >= 62)
                .sorted(Comparator.comparingInt(QuestionDuplicateCandidateDTO::getSimilarityScore).reversed()
                        .thenComparing(candidate -> safeInt(candidate.getQuestion().getAppearCount(), 0), Comparator.reverseOrder())
                        .thenComparing(candidate -> String.valueOf(candidate.getQuestion().getQuestionText())))
                .limit(8)
                .toList();
        return candidates;
    }

    private int semanticSimilarityScore(InterviewQuestionPO first, InterviewQuestionPO second) {
        Set<String> firstTokens = questionSemanticTokens(first);
        Set<String> secondTokens = questionSemanticTokens(second);
        if (firstTokens.isEmpty() || secondTokens.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new LinkedHashSet<>(firstTokens);
        intersection.retainAll(secondTokens);
        Set<String> union = new LinkedHashSet<>(firstTokens);
        union.addAll(secondTokens);
        int score = (int) Math.round((intersection.size() * 100.0) / Math.max(1, union.size()));
        if (!clean(first.getCompany()).isBlank() && clean(first.getCompany()).equals(clean(second.getCompany()))) {
            score += 8;
        }
        if (!clean(first.getPosition()).isBlank() && clean(first.getPosition()).equals(clean(second.getPosition()))) {
            score += 5;
        }
        if (hasSharedTechnicalKeyword(first.getQuestionText(), second.getQuestionText())) {
            score += 10;
        }
        return Math.max(0, Math.min(100, score));
    }

    private String semanticSimilarityReason(InterviewQuestionPO first, InterviewQuestionPO second) {
        List<String> reasons = new java.util.ArrayList<>();
        if (hasSharedTechnicalKeyword(first.getQuestionText(), second.getQuestionText())) {
            reasons.add("核心技术词一致");
        }
        if (!clean(first.getCompany()).isBlank() && clean(first.getCompany()).equals(clean(second.getCompany()))) {
            reasons.add("公司一致");
        }
        if (!clean(first.getPosition()).isBlank() && clean(first.getPosition()).equals(clean(second.getPosition()))) {
            reasons.add("岗位一致");
        }
        if (reasons.isEmpty()) {
            reasons.add("题干语义词重合度高");
        }
        return String.join(" / ", reasons);
    }

    private Set<String> questionSemanticTokens(InterviewQuestionPO question) {
        Set<String> tokens = new LinkedHashSet<>();
        addSemanticTokens(tokens, question.getQuestionText());
        addSemanticTokens(tokens, question.getExamPoint());
        return tokens;
    }

    private void addSemanticTokens(Set<String> tokens, String value) {
        String text = normalizeQuestion(value)
                .replace("如何", "")
                .replace("怎么", "")
                .replace("怎样", "")
                .replace("为什么", "")
                .replace("请", "")
                .replace("说说", "")
                .replace("讲讲", "")
                .replace("实现", "")
                .replace("保证", "")
                .replace("处理", "")
                .replace("方案", "")
                .replace("问题", "")
                .replace("一致性", "一致");
        if (text.isBlank()) {
            return;
        }
        for (String keyword : TECHNICAL_KEYWORDS) {
            if (text.contains(keyword)) {
                tokens.add(keyword);
            }
        }
        for (int size : new int[]{4, 3, 2}) {
            for (int i = 0; i + size <= text.length(); i++) {
                String token = text.substring(i, i + size);
                if (!SEMANTIC_STOP_TOKENS.contains(token)) {
                    tokens.add(token);
                }
            }
        }
    }

    private boolean hasSharedTechnicalKeyword(String first, String second) {
        String a = normalizeQuestion(first);
        String b = normalizeQuestion(second);
        return TECHNICAL_KEYWORDS.stream().anyMatch(keyword -> a.contains(keyword) && b.contains(keyword));
    }

    private Long canonicalIdFor(InterviewQuestionPO question) {
        if (question == null) {
            return null;
        }
        return question.getCanonicalId() == null ? question.getId() : question.getCanonicalId();
    }

    private Map<String, CompanyNameStat> aggregateCompanyNameStats(int limit) {
        Map<String, CompanyNameStat> stats = new HashMap<>();
        mergeCompanyNameStats(stats, questionMapper.countCompaniesForAliasCandidates(limit), true);
        mergeCompanyNameStats(stats, postMapper.countInterviewCompaniesForAliasCandidates(limit), false);
        return stats;
    }

    private void mergeCompanyNameStats(Map<String, CompanyNameStat> stats, List<Map<String, Object>> rows, boolean questionSource) {
        if (rows == null) {
            return;
        }
        rows.forEach(row -> {
            Object rawName = row.get("name");
            String name = rawName == null ? "" : clean(String.valueOf(rawName));
            String key = aliasCandidateKey(name);
            long count = asLong(row.get("count"));
            if (key.isBlank() || count <= 0) {
                return;
            }
            CompanyNameStat current = stats.get(key);
            long questionCount = (current == null ? 0L : current.questionCount()) + (questionSource ? count : 0L);
            long postCount = (current == null ? 0L : current.postCount()) + (questionSource ? 0L : count);
            String displayName = current == null || name.length() > current.name().length() ? name : current.name();
            stats.put(key, new CompanyNameStat(displayName, questionCount, postCount));
        });
    }

    private AliasMatch matchCompanyAlias(String first, String second) {
        String firstKey = aliasCandidateKey(first);
        String secondKey = aliasCandidateKey(second);
        if (firstKey.isBlank() || secondKey.isBlank() || firstKey.equals(secondKey)) {
            return null;
        }
        String mappedFirst = knownCompanyCanonical(firstKey);
        String mappedSecond = knownCompanyCanonical(secondKey);
        if (!mappedFirst.equals(firstKey) || !mappedSecond.equals(secondKey)) {
            if (mappedFirst.equals(mappedSecond)) {
                String canonical = preferredCanonicalName(mappedFirst, first, second);
                String alias = aliasCandidateKey(canonical).equals(firstKey) ? second : first;
                return new AliasMatch(canonical, alias, "命中常见中英文/简称映射");
            }
            return null;
        }
        if (firstKey.contains(secondKey) || secondKey.contains(firstKey)) {
            String canonical = firstKey.length() >= secondKey.length() ? first : second;
            String alias = firstKey.length() >= secondKey.length() ? second : first;
            if (aliasCandidateKey(canonical).length() - aliasCandidateKey(alias).length() >= 1) {
                return new AliasMatch(canonical, alias, "名称包含关系，疑似简称或历史名称");
            }
        }
        return null;
    }

    private String preferredCanonicalName(String canonicalKey, String first, String second) {
        if (aliasCandidateKey(first).equals(canonicalKey)) {
            return first;
        }
        if (aliasCandidateKey(second).equals(canonicalKey)) {
            return second;
        }
        return switch (canonicalKey) {
            case "字节跳动" -> "字节跳动";
            case "腾讯" -> "腾讯";
            case "阿里巴巴" -> "阿里巴巴";
            case "美团" -> "美团";
            case "拼多多" -> "拼多多";
            case "小米" -> "小米";
            case "百度" -> "百度";
            case "京东" -> "京东";
            case "快手" -> "快手";
            case "滴滴" -> "滴滴";
            case "华为" -> "华为";
            default -> first.length() >= second.length() ? first : second;
        };
    }

    private String knownCompanyCanonical(String key) {
        return switch (key) {
            case "bytedance", "字节", "字节跳动", "toutiao", "今日头条", "抖音" -> "字节跳动";
            case "tencent", "qq", "微信", "腾讯" -> "腾讯";
            case "alibaba", "ali", "阿里", "阿里巴巴", "蚂蚁", "antgroup" -> "阿里巴巴";
            case "meituan", "美团点评", "美团" -> "美团";
            case "pdd", "拼多多", "temu" -> "拼多多";
            case "xiaomi", "mi", "小米" -> "小米";
            case "baidu", "百度" -> "百度";
            case "jd", "jingdong", "京东" -> "京东";
            case "kuaishou", "快手" -> "快手";
            case "didi", "滴滴" -> "滴滴";
            case "huawei", "华为" -> "华为";
            default -> key;
        };
    }

    private String aliasCandidateKey(String value) {
        return clean(value).toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .replace("（", "(")
                .replace("）", ")")
                .replaceAll("[()（）【】._·,，-]", "");
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private record CompanyNameStat(String name, long questionCount, long postCount) {
        long totalCount() {
            return questionCount + postCount;
        }
    }

    private record AliasMatch(String canonicalCompany, String alias, String reason) {
    }

    private String normalizeTargetType(String type) {
        String value = clean(type);
        if (List.of("company", "position", "tag").contains(value)) {
            return value;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private String normalizeTargetPriority(String priority) {
        String value = clean(priority);
        return List.of("low", "medium", "high", "urgent").contains(value) ? value : "medium";
    }

    private void ensureQuestionVisible(Long questionId) {
        if (questionMapper.selectVisibleByIds(List.of(questionId), false).isEmpty()) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private String canonicalCompany(String company) {
        String name = clean(company);
        if (name.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        CompanyAliasPO alias = companyAliasMapper.findEnabledByName(name);
        return alias == null ? name : alias.getCanonicalCompany();
    }

    private List<PostBriefDTO> recentCompanyPosts(String company) {
        List<PostPO> posts = postMapper.selectRecentInterviewPostsByCompany(company, 8);
        return postFacade.batchGetPosts(posts.stream().map(PostPO::getId).toList()).values().stream()
                .sorted(Comparator.comparing(PostBriefDTO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private AiTaskDTO toTaskDto(AiExtractTaskPO task) {
        if (task == null) {
            return null;
        }
        return AiTaskDTO.builder()
                .id(task.getId())
                .postId(task.getPostId())
                .taskType(task.getTaskType())
                .taskStatus(task.getTaskStatus())
                .retryCount(task.getRetryCount())
                .questionCount(task.getQuestionCount())
                .errorMessage(task.getErrorMessage())
                .createTime(task.getCreateTime())
                .updateTime(task.getUpdateTime())
                .build();
    }

    private List<Long> safeTagIds(List<Long> extractedTagIds, PostDTO post) {
        Set<Long> ids = new LinkedHashSet<>();
        if (extractedTagIds != null) {
            extractedTagIds.stream().filter(id -> id != null && id > 0).limit(10).forEach(ids::add);
        }
        if (ids.isEmpty() && post.getTags() != null) {
            post.getTags().stream().map(com.offerlab.community.post.api.dto.TagDTO::getId)
                    .filter(id -> id != null && id > 0)
                    .limit(10)
                    .forEach(ids::add);
        }
        return ids.stream().toList();
    }

    private List<CompanyPrepDTO.NameCountDTO> toNameCounts(List<Map<String, Object>> rows) {
        if (rows == null) {
            return List.of();
        }
        return rows.stream().map(row -> CompanyPrepDTO.NameCountDTO.builder()
                .name(String.valueOf(row.getOrDefault("name", "")))
                .count(asLong(row.get("count")))
                .build()).toList();
    }

    private List<CompanyPrepDTO.NameCountDTO> toInterviewResultCounts(List<Map<String, Object>> rows) {
        if (rows == null) {
            return List.of();
        }
        return rows.stream()
                .map(row -> CompanyPrepDTO.NameCountDTO.builder()
                        .name(interviewResultLabel(row.get("name")))
                        .count(asLong(row.get("count")))
                        .build())
                .toList();
    }

    private long sumCounts(List<CompanyPrepDTO.NameCountDTO> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0L;
        }
        return rows.stream().mapToLong(row -> row.getCount() == null ? 0L : row.getCount()).sum();
    }

    private LocalDateTime maxTime(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }

    private LocalDateTime asLocalDateTime(Object value) {
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.util.Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), java.time.ZoneId.systemDefault());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return LocalDateTime.parse(text.replace(' ', 'T'));
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private String interviewResultLabel(Object value) {
        int code = value instanceof Number number ? number.intValue() : safeParseInt(value);
        return switch (code) {
            case 1 -> "已 offer";
            case 2 -> "待结果";
            case 3 -> "已挂";
            default -> "未选择";
        };
    }

    private int safeParseInt(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private int safeInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private ReviewSchedule nextReviewSchedule(String status, UserQuestionProgressPO existing, LocalDateTime now) {
        int currentInterval = Math.max(1, existing == null ? 1 : safeInt(existing.getReviewIntervalDays(), 1));
        int currentCount = Math.max(0, existing == null ? 0 : safeInt(existing.getReviewCount(), 0));
        return switch (status) {
            case "todo" -> new ReviewSchedule(null, existing == null ? null : existing.getLastReviewedAt(), 0, 1);
            case "learning" -> new ReviewSchedule(now.plusDays(1), existing == null ? null : existing.getLastReviewedAt(), 0, currentInterval);
            case "review" -> new ReviewSchedule(now, existing == null ? null : existing.getLastReviewedAt(), 0, currentInterval);
            case "mastered" -> {
                int nextInterval = currentCount <= 0 ? 3 : Math.min(30, Math.max(3, currentInterval * 2));
                yield new ReviewSchedule(now.plusDays(nextInterval), now, 1, nextInterval);
            }
            default -> throw new BizException(ErrorCode.PARAM_ERROR);
        };
    }

    private String normalizeProgress(String status) {
        String value = clean(status);
        if (List.of("todo", "learning", "mastered", "review").contains(value)) {
            return value;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private String normalizeMistakeReason(String reason) {
        String value = clean(reason);
        if (value.isBlank()) {
            return null;
        }
        if (List.of("concept", "project", "memory", "expression", "careless", "other").contains(value)) {
            return value;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private String normalizeOptionalMistakeReason(String reason) {
        String value = clean(reason);
        if ("any".equals(value)) {
            return value;
        }
        return value.isBlank() ? null : normalizeMistakeReason(value);
    }

    private String normalizeOptionalProgress(String status) {
        String value = clean(status);
        return value.isBlank() ? null : normalizeProgress(value);
    }

    private List<Long> normalizeTagIds(Collection<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        return tagIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .limit(20)
                .toList();
    }

    private String normalizeDifficulty(String difficulty) {
        String value = clean(difficulty);
        if (value.isBlank() || List.of("easy", "medium", "hard").contains(value)) {
            return value.isBlank() ? "medium" : value;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private void validateQuestionStatus(Integer status) {
        if (!List.of(QuestionConstants.QUESTION_PENDING, QuestionConstants.QUESTION_APPROVED, QuestionConstants.QUESTION_HIDDEN).contains(status)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    private int normalizeAliasStatus(Integer status) {
        if (status == null || status == 1) {
            return 1;
        }
        if (status == 0) {
            return 0;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
    }

    private String taskStatusName(Integer status) {
        if (status == null) {
            return "none";
        }
        return switch (status) {
            case QuestionConstants.TASK_PENDING -> "pending";
            case QuestionConstants.TASK_RUNNING -> "running";
            case QuestionConstants.TASK_SUCCEEDED -> "succeeded";
            case QuestionConstants.TASK_FAILED -> "failed";
            default -> "unknown";
        };
    }

    private String normalizeSort(String sort) {
        String value = clean(sort);
        if (List.of("latest", "appear", "hot", "relevance").contains(value)) {
            return value;
        }
        return "latest";
    }

    private int score(InterviewQuestionPO po) {
        int score = 20;
        if (po.getQuestionText() != null && po.getQuestionText().length() >= 20) score += 20;
        if (po.getCompany() != null && !po.getCompany().isBlank()) score += 15;
        if (po.getPosition() != null && !po.getPosition().isBlank()) score += 10;
        if (po.getInterviewRound() != null && !po.getInterviewRound().isBlank()) score += 5;
        if (po.getExamPoint() != null && !po.getExamPoint().isBlank()) score += 8;
        if (po.getReferenceAnswer() != null && po.getReferenceAnswer().length() >= 40) score += 10;
        if (po.getSourceSnippet() != null && !po.getSourceSnippet().isBlank()) score += 5;
        if (po.getConfidence() != null) score += po.getConfidence().multiply(BigDecimal.valueOf(30)).intValue();
        return Math.min(score, 100);
    }

    private String extValue(String extJson, String field) {
        if (extJson == null || extJson.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(extJson);
            return clean(node.path(field).asText(""));
        } catch (Exception e) {
            return "";
        }
    }

    private void addIfMatches(Set<String> names, Object value, String prefix) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (!text.isBlank() && (prefix.isBlank() || text.toLowerCase().contains(prefix))) {
            names.add(text);
        }
    }

    private String firstNonBlank(String first, String second) {
        String a = cleanToNull(first);
        return a == null ? cleanToNull(second) : a;
    }

    private String cleanToNull(String value) {
        String text = clean(value);
        return text.isBlank() ? null : text;
    }

    private String adminText(String value, int max) {
        return value == null ? null : limit(clean(value), max);
    }

    private String summaryText(String value, int max) {
        String text = clean(value)
                .replaceAll("[#*`>\\[\\]()_!~\\-]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return text.isBlank() ? null : limit(text, max);
    }

    private String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String normalizeQuestion(String value) {
        return clean(value).toLowerCase().replaceAll("\\s+", "").replaceAll("[?？。.!！,，:：;；]", "");
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(value.hashCode());
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
