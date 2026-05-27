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
import com.offerlab.community.question.api.dto.AiTaskDTO;
import com.offerlab.community.question.api.dto.CompanyAliasCmd;
import com.offerlab.community.question.api.dto.CompanyAliasDTO;
import com.offerlab.community.question.api.dto.CompanyPrepDTO;
import com.offerlab.community.question.api.dto.PostQuestionBlockDTO;
import com.offerlab.community.question.api.dto.PrepTargetCmd;
import com.offerlab.community.question.api.dto.PrepTargetDTO;
import com.offerlab.community.question.api.dto.QuestionAdminUpdateCmd;
import com.offerlab.community.question.api.dto.QuestionDTO;
import com.offerlab.community.question.api.dto.QuestionDetailDTO;
import com.offerlab.community.question.api.dto.QuestionQuery;
import com.offerlab.community.question.api.dto.QuestionTagDTO;
import com.offerlab.community.question.api.dto.UserPrepOverviewDTO;
import com.offerlab.community.question.infrastructure.persistence.mapper.AiExtractTaskMapper;
import com.offerlab.community.question.infrastructure.persistence.mapper.CompanyAliasMapper;
import com.offerlab.community.question.infrastructure.persistence.mapper.InterviewQuestionMapper;
import com.offerlab.community.question.infrastructure.persistence.mapper.InterviewQuestionTagMapper;
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
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionFacadeImpl implements QuestionFacade {
    private final InterviewQuestionMapper questionMapper;
    private final InterviewQuestionTagMapper questionTagMapper;
    private final AiExtractTaskMapper taskMapper;
    private final UserQuestionProgressMapper progressMapper;
    private final UserPrepTargetMapper prepTargetMapper;
    private final CompanyAliasMapper companyAliasMapper;
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
        } catch (Exception e) {
            log.warn("question extraction failed: postId={} taskId={}", post.getId(), task.getId(), e);
            fail(task, e.getMessage());
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
        List<InterviewQuestionPO> rows = questionMapper.searchPublic(
                clean(q.getKeyword()), clean(q.getCompany()), clean(q.getPosition()), clean(q.getDifficulty()),
                clean(q.getRound()), q.getTagIds(), q.getStartTime(), q.getEndTime(), normalizeSort(q.getSort()),
                offset, pageSize + 1);
        if (!clean(q.getKeyword()).isBlank()) {
            rows = searchQuestionsByEs(q, offset, pageSize + 1).orElse(rows);
        }
        boolean hasMore = rows.size() > pageSize;
        List<InterviewQuestionPO> pageRows = hasMore ? rows.subList(0, pageSize) : rows;
        return PageResult.of(toQuestionDtos(pageRows, viewerUid), hasMore ? String.valueOf(page + 1) : null, hasMore);
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
            progressMapper.upsert(idGen.nextId(), uid, questionId, null, favorite ? 1 : 0, null);
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
        if (existing == null) {
            progressMapper.upsert(idGen.nextId(), uid, questionId, normalized, 0, null);
        } else {
            progressMapper.updateStatus(uid, questionId, normalized);
        }
        return Map.of("questionId", questionId, "status", normalized);
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
        List<UserQuestionProgressPO> recent = progressMapper.selectRecentByUser(uid, 200);
        List<QuestionDTO> favorites = questionsFromProgress(progressMapper.selectRecentFavorites(uid, 8), uid);
        List<QuestionDTO> reviews = questionsFromProgress(progressMapper.selectRecentByStatus(uid, "review", 8), uid);
        List<PrepTargetDTO> targets = listPrepTargets(uid);
        List<QuestionDTO> recommended = recommendedForTargets(targets, uid);
        return UserPrepOverviewDTO.builder()
                .favoriteCount(recent.stream().filter(p -> Objects.equals(p.getFavorite(), 1)).count())
                .todoCount(recent.stream().filter(p -> "todo".equals(p.getProgressStatus())).count())
                .learningCount(recent.stream().filter(p -> "learning".equals(p.getProgressStatus())).count())
                .masteredCount(recent.stream().filter(p -> "mastered".equals(p.getProgressStatus())).count())
                .reviewCount(recent.stream().filter(p -> "review".equals(p.getProgressStatus())).count())
                .targets(targets)
                .favoriteQuestions(favorites)
                .reviewQuestions(reviews)
                .recommendedQuestions(recommended)
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
        if (value.isBlank() || value.length() > 128) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        prepTargetMapper.insertIgnore(idGen.nextId(), uid, type, value);
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
        return toQuestionDtos(questionMapper.selectAdminRecent(status, safeLimit), null);
    }

    @Override
    public PageResult<QuestionDTO> pageAdminQuestions(Integer status, int page, int pageSize) {
        int safePageSize = Math.max(1, Math.min(pageSize <= 0 ? 30 : pageSize, 100));
        int safePage = Math.max(1, page <= 0 ? 1 : page);
        List<QuestionDTO> items = listAdminQuestions(status, safePage * safePageSize).stream()
                .skip((long) (safePage - 1) * safePageSize)
                .limit(safePageSize)
                .toList();
        long total = questionMapper.countAdminRecent(status);
        boolean hasMore = (long) safePage * safePageSize < total;
        return PageResult.<QuestionDTO>builder()
                .items(items)
                .nextCursor(hasMore ? String.valueOf(safePage + 1) : null)
                .hasMore(hasMore)
                .total(total)
                .build();
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
        update.setAnswerHint(cmd.getAnswerHint());
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
    public List<CompanyAliasDTO> listCompanyAliases(String keyword, int limit) {
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 100));
        return companyAliasMapper.listAdmin(clean(keyword), safeLimit).stream()
                .map(this::toCompanyAliasDto)
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
        List<QuestionDTO> topQuestions = toQuestionDtos(questionMapper.selectTopByCompany(canonical, 10), viewerUid);
        List<PostBriefDTO> recentPosts = recentCompanyPosts(canonical);
        List<CompanyPrepDTO.NameCountDTO> hotPositions = toNameCounts(questionMapper.countPositionsByCompany(canonical, 8));
        List<CompanyPrepDTO.NameCountDTO> topTags = toNameCounts(questionTagMapper.countTagsByCompany(canonical, 10));
        List<CompanyPrepDTO.NameCountDTO> trend30Days = toNameCounts(questionMapper.countByCompanySince(canonical, LocalDateTime.now().minusDays(30)));
        List<CompanyPrepDTO.NameCountDTO> trend90Days = toNameCounts(questionMapper.countByCompanySince(canonical, LocalDateTime.now().minusDays(90)));
        return CompanyPrepDTO.builder()
                .company(canonical)
                .aliases(aliases)
                .relatedPositionCount((int) questionMapper.countDistinctPositionsByCompany(canonical))
                .recentPosts(recentPosts)
                .topQuestions(topQuestions)
                .topTags(topTags)
                .hotPositions(hotPositions)
                .trend30Days(trend30Days)
                .trend90Days(trend90Days)
                .myProgress(viewerUid == null ? null : progressSummary(viewerUid, topQuestions.stream().map(QuestionDTO::getId).toList()))
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
        List<Long> ids = questionMapper.selectByPostId(postId, true).stream()
                .map(InterviewQuestionPO::getId)
                .toList();
        questionMapper.hideByPostId(postId);
        ids.forEach(questionSearchIndexer::indexQuestion);
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
        List<Long> oldQuestionIds = questionMapper.selectByPostId(post.getId(), true).stream()
                .map(InterviewQuestionPO::getId)
                .toList();
        questionTagMapper.deleteByPostId(post.getId());
        questionMapper.delete(new LambdaQueryWrapper<InterviewQuestionPO>()
                .eq(InterviewQuestionPO::getSourcePostId, post.getId()));
        oldQuestionIds.forEach(questionSearchIndexer::deleteQuestion);
        int count = 0;
        Set<String> affectedCompanies = new LinkedHashSet<>();
        for (ExtractedQuestion item : extracted) {
            Long questionId = idGen.nextId();
            InterviewQuestionPO po = new InterviewQuestionPO();
            po.setId(questionId);
            po.setQuestionText(clean(item.getQuestionText()));
            po.setNormalizedHash(hash(normalizeQuestion(item.getQuestionText())));
            po.setAnswerHint(cleanToNull(item.getAnswerHint()));
            po.setCompany(firstNonBlank(item.getCompany(), extValue(post.getExtJson(), "company")));
            po.setPosition(firstNonBlank(item.getPosition(), extValue(post.getExtJson(), "position")));
            po.setInterviewRound(cleanToNull(item.getInterviewRound()));
            po.setDifficulty(firstNonBlank(item.getDifficulty(), "medium"));
            po.setConfidence(item.getConfidence() == null ? new BigDecimal("0.5000") : item.getConfidence());
            po.setSourcePostId(post.getId());
            po.setSourceAuthorUid(post.getAuthorId());
            po.setStatus(QuestionConstants.QUESTION_APPROVED);
            po.setAppearCount(1);
            po.setQualityScore(score(po));
            questionMapper.insert(po);
            questionSearchIndexer.indexQuestion(questionId);
            if (po.getCompany() != null && !po.getCompany().isBlank()) {
                affectedCompanies.add(po.getCompany());
            }
            for (Long tagId : safeTagIds(item.getTagIds(), post)) {
                questionTagMapper.insertIgnore(idGen.nextId(), questionId, tagId);
            }
            count++;
        }
        affectedCompanies.forEach(this::evictQuestionCachesByCompany);
        return count;
    }

    private java.util.Optional<List<InterviewQuestionPO>> searchQuestionsByEs(QuestionQuery query, int offset, int limit) {
        return questionSearchIndexer.search(query, offset, limit)
                .filter(ids -> !ids.isEmpty())
                .map(ids -> {
                    Map<Long, InterviewQuestionPO> byId = questionMapper.selectVisibleByIds(ids, false).stream()
                            .collect(Collectors.toMap(InterviewQuestionPO::getId, row -> row, (a, b) -> a));
                    return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
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

    private List<QuestionDTO> toQuestionDtos(List<InterviewQuestionPO> rows, Long viewerUid) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(InterviewQuestionPO::getId).toList();
        Map<Long, List<QuestionTagDTO>> tags = tagsByQuestionIds(ids);
        Map<Long, UserQuestionProgressPO> progress = progressByQuestionIds(viewerUid, ids);
        return rows.stream().map(row -> {
            UserQuestionProgressPO p = progress.get(row.getId());
            return QuestionDTO.builder()
                    .id(row.getId())
                    .canonicalId(row.getCanonicalId())
                    .questionText(row.getQuestionText())
                    .answerHint(row.getAnswerHint())
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
                    .sourcePostCount(row.getAppearCount())
                    .createTime(row.getCreateTime())
                    .updateTime(row.getUpdateTime())
                    .build();
        }).toList();
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

    private List<QuestionDTO> questionsFromProgress(List<UserQuestionProgressPO> progress, Long viewerUid) {
        if (progress == null || progress.isEmpty()) {
            return List.of();
        }
        List<Long> ids = progress.stream().map(UserQuestionProgressPO::getQuestionId).toList();
        return toQuestionDtos(questionMapper.selectVisibleByIds(ids, false), viewerUid);
    }

    private List<QuestionDTO> recommendedForTargets(List<PrepTargetDTO> targets, Long viewerUid) {
        if (targets == null || targets.isEmpty()) {
            return toQuestionDtos(questionMapper.selectRecommended(10), viewerUid);
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (PrepTargetDTO target : targets) {
            QuestionQuery query = new QuestionQuery();
            if ("company".equals(target.getTargetType())) {
                query.setCompany(target.getTargetValue());
            } else if ("position".equals(target.getTargetType())) {
                query.setPosition(target.getTargetValue());
            } else {
                query.setKeyword(target.getTargetValue());
            }
            questionMapper.searchPublic(null, clean(query.getCompany()), clean(query.getPosition()), null,
                    null, null, null, null, "hot", 0, 6)
                    .stream()
                    .map(InterviewQuestionPO::getId)
                    .forEach(ids::add);
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

    private PrepTargetDTO toPrepTargetDto(UserPrepTargetPO po) {
        return PrepTargetDTO.builder()
                .id(po.getId())
                .uid(po.getUid())
                .targetType(po.getTargetType())
                .targetValue(po.getTargetValue())
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

    private String normalizeTargetType(String type) {
        String value = clean(type);
        if (List.of("company", "position", "tag").contains(value)) {
            return value;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
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

    private CompanyPrepDTO.UserPrepSummaryDTO progressSummary(Long uid, Collection<Long> questionIds) {
        Map<Long, UserQuestionProgressPO> progress = progressByQuestionIds(uid, questionIds);
        return CompanyPrepDTO.UserPrepSummaryDTO.builder()
                .favoriteCount(progress.values().stream().filter(p -> Objects.equals(p.getFavorite(), 1)).count())
                .learningCount(progress.values().stream().filter(p -> "learning".equals(p.getProgressStatus())).count())
                .masteredCount(progress.values().stream().filter(p -> "mastered".equals(p.getProgressStatus())).count())
                .reviewCount(progress.values().stream().filter(p -> "review".equals(p.getProgressStatus())).count())
                .build();
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

    private long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String normalizeProgress(String status) {
        String value = clean(status);
        if (List.of("todo", "learning", "mastered", "review").contains(value)) {
            return value;
        }
        throw new BizException(ErrorCode.PARAM_ERROR);
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
