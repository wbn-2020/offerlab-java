package com.offerlab.community.question.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.question.api.dto.MockInterviewAnswerDTO;
import com.offerlab.community.question.api.dto.MockInterviewDraftCmd;
import com.offerlab.community.question.api.dto.MockInterviewSessionDTO;
import com.offerlab.community.question.api.dto.MockInterviewStartCmd;
import com.offerlab.community.question.api.dto.MockInterviewStatsDTO;
import com.offerlab.community.question.api.dto.MockInterviewSubmitCmd;
import com.offerlab.community.question.api.dto.QuestionDTO;
import com.offerlab.community.question.api.dto.QuestionTagDTO;
import com.offerlab.community.question.infrastructure.persistence.mapper.InterviewQuestionMapper;
import com.offerlab.community.question.infrastructure.persistence.mapper.InterviewQuestionTagMapper;
import com.offerlab.community.question.infrastructure.persistence.mapper.MockInterviewAnswerMapper;
import com.offerlab.community.question.infrastructure.persistence.mapper.MockInterviewSessionMapper;
import com.offerlab.community.question.infrastructure.persistence.mapper.UserPrepTargetMapper;
import com.offerlab.community.question.infrastructure.persistence.mapper.UserQuestionProgressMapper;
import com.offerlab.community.question.infrastructure.persistence.po.InterviewQuestionPO;
import com.offerlab.community.question.infrastructure.persistence.po.MockInterviewAnswerPO;
import com.offerlab.community.question.infrastructure.persistence.po.MockInterviewSessionPO;
import com.offerlab.community.question.infrastructure.persistence.po.UserPrepTargetPO;
import com.offerlab.community.question.infrastructure.persistence.po.UserQuestionProgressPO;
import com.offerlab.community.post.infrastructure.persistence.projection.PostTagView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MockInterviewService {
    private static final String STATUS_STARTED = "started";
    private static final String STATUS_COMPLETED = "completed";
    private static final int INSIGHT_WINDOW_SIZE = 20;
    private static final int WEAK_ANSWER_LIMIT = 6;
    private static final int PERSONAL_CANDIDATE_LIMIT = 20;

    private final MockInterviewSessionMapper sessionMapper;
    private final MockInterviewAnswerMapper answerMapper;
    private final InterviewQuestionMapper questionMapper;
    private final InterviewQuestionTagMapper questionTagMapper;
    private final UserQuestionProgressMapper progressMapper;
    private final UserPrepTargetMapper prepTargetMapper;
    private final SnowflakeIdGenerator idGen;
    private final MockInterviewAiReviewService aiReviewService;

    @Transactional
    public MockInterviewSessionDTO start(Long uid, MockInterviewStartCmd cmd) {
        int questionCount = Math.max(1, Math.min(cmd == null || cmd.getQuestionCount() == null ? 5 : cmd.getQuestionCount(), 10));
        String company = clean(cmd == null ? null : cmd.getCompany());
        String position = clean(cmd == null ? null : cmd.getPosition());
        String difficulty = normalizeDifficulty(cmd == null ? null : cmd.getDifficulty());
        String focusTag = clean(cmd == null ? null : cmd.getFocusTag());
        List<InterviewQuestionPO> questions = pickQuestions(uid, company, position, difficulty, focusTag, questionCount);
        if (questions.isEmpty()) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        MockInterviewSessionPO session = new MockInterviewSessionPO();
        session.setId(idGen.nextId());
        session.setUid(uid);
        session.setCompany(blankToNull(company));
        session.setPosition(blankToNull(position));
        session.setDifficulty(blankToNull(difficulty));
        session.setFocusTag(blankToNull(focusTag));
        session.setQuestionCount(questions.size());
        session.setAnsweredCount(0);
        session.setTotalScore(0);
        session.setDurationSeconds(0);
        session.setStatus(STATUS_STARTED);
        sessionMapper.insert(session);

        int sequence = 1;
        for (InterviewQuestionPO question : questions) {
            MockInterviewAnswerPO answer = new MockInterviewAnswerPO();
            answer.setId(idGen.nextId());
            answer.setSessionId(session.getId());
            answer.setUid(uid);
            answer.setQuestionId(question.getId());
            answer.setSequenceNo(sequence++);
            fillQuestionSnapshot(answer, question);
            answer.setAnswerText("");
            answer.setSelfReview("");
            answer.setScore(0);
            answerMapper.insert(answer);
        }
        return get(uid, session.getId());
    }

    @Transactional
    public MockInterviewSessionDTO submit(Long uid, Long sessionId, MockInterviewSubmitCmd cmd) {
        MockInterviewSessionPO session = sessionMapper.selectByUser(sessionId, uid);
        if (session == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (STATUS_COMPLETED.equals(session.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        List<MockInterviewAnswerPO> existing = answerMapper.selectBySession(sessionId, uid);
        Map<Long, MockInterviewSubmitCmd.AnswerCmd> byQuestionId = (cmd == null || cmd.getAnswers() == null ? List.<MockInterviewSubmitCmd.AnswerCmd>of() : cmd.getAnswers())
                .stream()
                .filter(item -> item.getQuestionId() != null)
                .collect(Collectors.toMap(MockInterviewSubmitCmd.AnswerCmd::getQuestionId, item -> item, (a, b) -> b, LinkedHashMap::new));
        int answered = 0;
        int totalScore = 0;
        for (MockInterviewAnswerPO old : existing) {
            MockInterviewSubmitCmd.AnswerCmd input = byQuestionId.get(old.getQuestionId());
            String answerText = limit(clean(input == null ? null : input.getAnswerText()), 4000);
            String selfReview = limit(clean(input == null ? null : input.getSelfReview()), 1000);
            int score = Math.max(0, Math.min(input == null || input.getScore() == null ? 0 : input.getScore(), 5));
            answerMapper.updateDraft(uid, sessionId, old.getQuestionId(), answerText, selfReview, score);
            old.setAnswerText(answerText);
            old.setSelfReview(selfReview);
            old.setScore(score);
            if (!answerText.isBlank()) {
                answered++;
                totalScore += score;
            }
        }
        int duration = Math.max(0, Math.min(cmd == null || cmd.getDurationSeconds() == null ? 0 : cmd.getDurationSeconds(), 7200));
        if (sessionMapper.complete(sessionId, uid, answered, totalScore, duration, STATUS_COMPLETED) == 0) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        if (Boolean.TRUE.equals(cmd == null ? null : cmd.getAiReviewEnabled())) {
            reviewAnswers(uid, sessionId, existing);
        }
        return get(uid, sessionId);
    }

    @Transactional
    public MockInterviewSessionDTO saveDraft(Long uid, Long sessionId, MockInterviewDraftCmd cmd) {
        MockInterviewSessionPO session = sessionMapper.selectByUser(sessionId, uid);
        if (session == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (STATUS_COMPLETED.equals(session.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        List<MockInterviewAnswerPO> existing = answerMapper.selectBySession(sessionId, uid);
        Map<Long, MockInterviewDraftCmd.AnswerCmd> byQuestionId = (cmd == null || cmd.getAnswers() == null ? List.<MockInterviewDraftCmd.AnswerCmd>of() : cmd.getAnswers())
                .stream()
                .filter(item -> item.getQuestionId() != null)
                .collect(Collectors.toMap(MockInterviewDraftCmd.AnswerCmd::getQuestionId, item -> item, (a, b) -> b, LinkedHashMap::new));
        int answered = 0;
        int totalScore = 0;
        for (MockInterviewAnswerPO old : existing) {
            MockInterviewDraftCmd.AnswerCmd input = byQuestionId.get(old.getQuestionId());
            String answerText = input == null ? clean(old.getAnswerText()) : limit(clean(input.getAnswerText()), 4000);
            String selfReview = input == null ? clean(old.getSelfReview()) : limit(clean(input.getSelfReview()), 1000);
            int score = Math.max(0, Math.min(input == null || input.getScore() == null ? Objects.requireNonNullElse(old.getScore(), 0) : input.getScore(), 5));
            answerMapper.updateDraft(uid, sessionId, old.getQuestionId(), answerText, selfReview, score);
            if (!answerText.isBlank()) {
                answered++;
                totalScore += score;
            }
        }
        int duration = Math.max(0, Math.min(cmd == null || cmd.getDurationSeconds() == null ? Objects.requireNonNullElse(session.getDurationSeconds(), 0) : cmd.getDurationSeconds(), 7200));
        if (sessionMapper.updateDraft(sessionId, uid, answered, totalScore, duration) == 0) {
            throw new BizException(ErrorCode.INVALID_STATUS);
        }
        return get(uid, sessionId);
    }

    public MockInterviewSessionDTO get(Long uid, Long sessionId) {
        MockInterviewSessionPO session = sessionMapper.selectByUser(sessionId, uid);
        if (session == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return toSessionDto(session, answerMapper.selectBySession(sessionId, uid));
    }

    public List<MockInterviewSessionDTO> recent(Long uid, int limit) {
        int safeLimit = Math.max(1, Math.min(limit <= 0 ? 5 : limit, 20));
        return toSessionDtos(uid, sessionMapper.selectRecentByUser(uid, safeLimit));
    }

    public MockInterviewStatsDTO stats(Long uid) {
        List<MockInterviewSessionDTO> recentSessions = recent(uid, INSIGHT_WINDOW_SIZE);
        List<MockInterviewSessionDTO> completedSessions = recentSessions.stream()
                .filter(session -> STATUS_COMPLETED.equals(session.getStatus()))
                .toList();
        List<MockInterviewSessionDTO> recentCompleted = completedSessions.stream().limit(5).toList();
        Map<String, Object> stats = sessionMapper.selectStatsByUser(uid);
        return MockInterviewStatsDTO.builder()
                .sessionCount(number(stats, "session_count"))
                .completedCount(number(stats, "completed_count"))
                .totalQuestionCount(number(stats, "total_question_count"))
                .answeredQuestionCount(number(stats, "answered_question_count"))
                .averageScorePercent(number(stats, "average_score_percent"))
                .bestScorePercent(number(stats, "best_score_percent"))
                .averageDurationSeconds(number(stats, "average_duration_seconds"))
                .insightWindowSize(INSIGHT_WINDOW_SIZE)
                .lastSession(recentSessions.stream().findFirst().orElse(null))
                .recentSessions(recentCompleted)
                .weakAnswers(weakAnswers(completedSessions))
                .focusTagInsights(insightsBy(completedSessions, MockInterviewSessionDTO::getFocusTag))
                .companyInsights(insightsBy(completedSessions, MockInterviewSessionDTO::getCompany))
                .positionInsights(insightsBy(completedSessions, MockInterviewSessionDTO::getPosition))
                .build();
    }

    private List<InterviewQuestionPO> pickQuestions(Long uid, String company, String position, String difficulty, String focusTag, int questionCount) {
        LinkedHashMap<Long, InterviewQuestionPO> result = new LinkedHashMap<>();
        int poolLimit = Math.max(PERSONAL_CANDIDATE_LIMIT, questionCount * 4);
        if (!focusTag.isBlank()) {
            addCandidates(result, questionMapper.selectMockInterviewQuestionsByTag(focusTag, difficulty, poolLimit), uid, questionCount);
            if (result.size() < questionCount && !difficulty.isBlank()) {
                addCandidates(result, questionMapper.selectMockInterviewQuestionsByTag(focusTag, null, poolLimit), uid, questionCount);
            }
        }
        addCandidates(result, reviewCandidateQuestions(uid, company, position, difficulty, poolLimit), uid, questionCount);
        addCandidates(result, questionMapper.selectMockInterviewQuestions(company, position, difficulty, poolLimit), uid, questionCount);
        addWeakTagCandidates(result, uid, difficulty, questionCount, poolLimit);
        if (result.size() < questionCount && !difficulty.isBlank()) {
            addCandidates(result, questionMapper.selectMockInterviewQuestions(company, position, null, poolLimit), uid, questionCount);
        }
        addPrepTargetCandidates(result, uid, difficulty, questionCount, poolLimit);
        if (result.size() < questionCount) {
            addCandidates(result, questionMapper.selectRecommended(poolLimit), uid, questionCount);
        }
        return result.values().stream().limit(questionCount).toList();
    }

    private void addWeakTagCandidates(LinkedHashMap<Long, InterviewQuestionPO> result, Long uid, String difficulty, int questionCount, int poolLimit) {
        if (uid == null || result.size() >= questionCount) {
            return;
        }
        LocalDateTime staleLearningBefore = LocalDateTime.now().minusDays(2);
        List<Map<String, Object>> weakTags = progressMapper.countWeaknessTags(uid, staleLearningBefore, 5);
        if (weakTags == null || weakTags.isEmpty()) {
            return;
        }
        for (Map<String, Object> row : weakTags) {
            String tagName = clean(String.valueOf(row.getOrDefault("name", "")));
            if (tagName.isBlank()) {
                continue;
            }
            addCandidates(result, questionMapper.selectMockInterviewQuestionsByTag(tagName, difficulty, poolLimit), uid, questionCount);
            if (result.size() < questionCount && !difficulty.isBlank()) {
                addCandidates(result, questionMapper.selectMockInterviewQuestionsByTag(tagName, null, poolLimit), uid, questionCount);
            }
            if (result.size() >= questionCount) {
                return;
            }
        }
    }

    private void addPrepTargetCandidates(LinkedHashMap<Long, InterviewQuestionPO> result, Long uid, String difficulty, int questionCount, int poolLimit) {
        if (uid == null || result.size() >= questionCount) {
            return;
        }
        List<UserPrepTargetPO> targets = prepTargetMapper.selectByUser(uid);
        if (targets == null || targets.isEmpty()) {
            return;
        }
        for (UserPrepTargetPO target : targets) {
            String type = clean(target.getTargetType());
            String value = clean(target.getTargetValue());
            if (value.isBlank()) {
                continue;
            }
            if ("tag".equals(type)) {
                addCandidates(result, questionMapper.selectMockInterviewQuestionsByTag(value, difficulty, poolLimit), uid, questionCount);
                if (result.size() < questionCount && !difficulty.isBlank()) {
                    addCandidates(result, questionMapper.selectMockInterviewQuestionsByTag(value, null, poolLimit), uid, questionCount);
                }
            } else if ("company".equals(type)) {
                addCandidates(result, questionMapper.selectMockInterviewQuestions(value, null, difficulty, poolLimit), uid, questionCount);
                if (result.size() < questionCount && !difficulty.isBlank()) {
                    addCandidates(result, questionMapper.selectMockInterviewQuestions(value, null, null, poolLimit), uid, questionCount);
                }
            } else if ("position".equals(type)) {
                addCandidates(result, questionMapper.selectMockInterviewQuestions(null, value, difficulty, poolLimit), uid, questionCount);
                if (result.size() < questionCount && !difficulty.isBlank()) {
                    addCandidates(result, questionMapper.selectMockInterviewQuestions(null, value, null, poolLimit), uid, questionCount);
                }
            }
            if (result.size() >= questionCount) {
                return;
            }
        }
    }

    private List<InterviewQuestionPO> reviewCandidateQuestions(Long uid, String company, String position, String difficulty, int poolLimit) {
        if (uid == null) {
            return List.of();
        }
        LocalDateTime staleLearningBefore = LocalDateTime.now().minusDays(2);
        List<Long> ids = progressMapper.selectReviewCandidates(uid, staleLearningBefore, poolLimit).stream()
                .map(UserQuestionProgressPO::getQuestionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, InterviewQuestionPO> byId = questionMapper.selectVisibleByIds(ids, false).stream()
                .collect(Collectors.toMap(InterviewQuestionPO::getId, row -> row, (a, b) -> a));
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .filter(question -> matchesFilter(question.getCompany(), company))
                .filter(question -> matchesFilter(question.getPosition(), position))
                .filter(question -> difficulty.isBlank() || difficulty.equals(question.getDifficulty()))
                .toList();
    }

    private void addCandidates(LinkedHashMap<Long, InterviewQuestionPO> result, List<InterviewQuestionPO> candidates, Long uid, int questionCount) {
        if (result.size() >= questionCount || candidates == null || candidates.isEmpty()) {
            return;
        }
        for (InterviewQuestionPO question : filterUnmastered(uid, candidates)) {
            if (question == null || question.getId() == null) {
                continue;
            }
            result.putIfAbsent(question.getId(), question);
            if (result.size() >= questionCount) {
                return;
            }
        }
    }

    private List<InterviewQuestionPO> filterUnmastered(Long uid, List<InterviewQuestionPO> candidates) {
        if (uid == null || candidates == null || candidates.isEmpty()) {
            return candidates == null ? List.of() : candidates;
        }
        List<Long> ids = candidates.stream()
                .map(InterviewQuestionPO::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, UserQuestionProgressPO> progressByQuestion = progressMapper.selectByUserAndQuestions(uid, ids).stream()
                .collect(Collectors.toMap(UserQuestionProgressPO::getQuestionId, row -> row, (a, b) -> a));
        return candidates.stream()
                .filter(question -> {
                    UserQuestionProgressPO progress = question == null ? null : progressByQuestion.get(question.getId());
                    return progress == null || !"mastered".equals(progress.getProgressStatus());
                })
                .toList();
    }

    private boolean matchesFilter(String value, String filter) {
        String normalizedFilter = clean(filter).toLowerCase(Locale.ROOT);
        if (normalizedFilter.isBlank()) {
            return true;
        }
        String normalizedValue = clean(value).toLowerCase(Locale.ROOT);
        return !normalizedValue.isBlank()
                && (normalizedValue.contains(normalizedFilter) || normalizedFilter.contains(normalizedValue));
    }

    private List<MockInterviewSessionDTO> toSessionDtos(Long uid, List<MockInterviewSessionPO> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }
        List<Long> sessionIds = sessions.stream().map(MockInterviewSessionPO::getId).toList();
        Map<Long, List<MockInterviewAnswerPO>> answersBySession = answerMapper.selectBySessions(uid, sessionIds).stream()
                .collect(Collectors.groupingBy(MockInterviewAnswerPO::getSessionId, LinkedHashMap::new, Collectors.toList()));
        return sessions.stream()
                .map(session -> toSessionDto(session, answersBySession.getOrDefault(session.getId(), List.of())))
                .toList();
    }

    private MockInterviewSessionDTO toSessionDto(MockInterviewSessionPO session, List<MockInterviewAnswerPO> answers) {
        return MockInterviewSessionDTO.builder()
                .id(session.getId())
                .company(session.getCompany())
                .position(session.getPosition())
                .difficulty(session.getDifficulty())
                .focusTag(session.getFocusTag())
                .questionCount(session.getQuestionCount())
                .answeredCount(session.getAnsweredCount())
                .totalScore(session.getTotalScore())
                .durationSeconds(session.getDurationSeconds())
                .status(session.getStatus())
                .createTime(session.getCreateTime())
                .updateTime(session.getUpdateTime())
                .answers(toAnswerDtos(answers))
                .build();
    }

    private List<MockInterviewAnswerDTO> toAnswerDtos(List<MockInterviewAnswerPO> answers) {
        if (answers == null || answers.isEmpty()) {
            return List.of();
        }
        List<Long> questionIds = answers.stream()
                .map(MockInterviewAnswerPO::getQuestionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, InterviewQuestionPO> questions = questionMapper.selectVisibleByIds(questionIds, false)
                .stream()
                .collect(Collectors.toMap(InterviewQuestionPO::getId, row -> row, (a, b) -> a));
        Map<Long, List<QuestionTagDTO>> tagsByQuestion = tagsByQuestionIds(questionIds);
        return answers.stream()
                .map(answer -> MockInterviewAnswerDTO.builder()
                        .id(answer.getId())
                        .sessionId(answer.getSessionId())
                        .questionId(answer.getQuestionId())
                        .sequenceNo(answer.getSequenceNo())
                        .questionTextSnapshot(answer.getQuestionTextSnapshot())
                        .answerHintSnapshot(answer.getAnswerHintSnapshot())
                        .companySnapshot(answer.getCompanySnapshot())
                        .positionSnapshot(answer.getPositionSnapshot())
                        .roundSnapshot(answer.getRoundSnapshot())
                        .difficultySnapshot(answer.getDifficultySnapshot())
                        .answerText(answer.getAnswerText())
                        .selfReview(answer.getSelfReview())
                        .score(answer.getScore())
                        .aiReviewed(Objects.requireNonNullElse(answer.getAiReviewed(), 0) == 1)
                        .aiScore(answer.getAiScore())
                        .aiCompleteness(answer.getAiCompleteness())
                        .aiProjectExpression(answer.getAiProjectExpression())
                        .aiFollowUpSuggestion(answer.getAiFollowUpSuggestion())
                        .aiReviewProvider(answer.getAiReviewProvider())
                        .createTime(answer.getCreateTime())
                        .question(toQuestionDto(questions.get(answer.getQuestionId()), answer, tagsByQuestion))
                        .build())
                .toList();
    }

    private void reviewAnswers(Long uid, Long sessionId, List<MockInterviewAnswerPO> answers) {
        if (answers == null || answers.isEmpty()) {
            return;
        }
        for (MockInterviewAnswerPO answer : answers) {
            MockInterviewAiReviewService.ReviewResult result = aiReviewService.review(answer);
            if (result == null) {
                continue;
            }
            answerMapper.updateAiReview(uid, sessionId, answer.getQuestionId(), result.score(),
                    result.completeness(), result.projectExpression(), result.followUpSuggestion(), result.provider());
        }
    }

    private QuestionDTO toQuestionDto(InterviewQuestionPO row, MockInterviewAnswerPO snapshot, Map<Long, List<QuestionTagDTO>> tagsByQuestion) {
        if (row == null) {
            if (snapshot == null || snapshot.getQuestionTextSnapshot() == null || snapshot.getQuestionTextSnapshot().isBlank()) {
                return null;
            }
            return QuestionDTO.builder()
                    .id(snapshot.getQuestionId())
                    .questionText(snapshot.getQuestionTextSnapshot())
                    .answerHint(snapshot.getAnswerHintSnapshot())
                    .company(snapshot.getCompanySnapshot())
                    .position(snapshot.getPositionSnapshot())
                    .interviewRound(snapshot.getRoundSnapshot())
                    .difficulty(snapshot.getDifficultySnapshot())
                    .status(1)
                    .appearCount(0)
                    .qualityScore(0)
                    .tags(List.of())
                    .favorite(false)
                    .sourcePostCount(0)
                    .build();
        }
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
                .tags(tagsByQuestion.getOrDefault(row.getId(), List.of()))
                .favorite(false)
                .sourcePostCount(row.getAppearCount())
                .createTime(row.getCreateTime())
                .updateTime(row.getUpdateTime())
                .build();
    }

    private void fillQuestionSnapshot(MockInterviewAnswerPO answer, InterviewQuestionPO question) {
        if (answer == null || question == null) {
            return;
        }
        answer.setQuestionTextSnapshot(question.getQuestionText());
        answer.setAnswerHintSnapshot(question.getAnswerHint());
        answer.setCompanySnapshot(question.getCompany());
        answer.setPositionSnapshot(question.getPosition());
        answer.setRoundSnapshot(question.getInterviewRound());
        answer.setDifficultySnapshot(question.getDifficulty());
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

    private List<MockInterviewAnswerDTO> weakAnswers(List<MockInterviewSessionDTO> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }
        return sessions.stream()
                .flatMap(session -> safeAnswers(session).stream())
                .filter(this::isWeakAnswer)
                .limit(WEAK_ANSWER_LIMIT)
                .toList();
    }

    private List<MockInterviewAnswerDTO> safeAnswers(MockInterviewSessionDTO session) {
        return session == null || session.getAnswers() == null ? List.of() : session.getAnswers();
    }

    private boolean isWeakAnswer(MockInterviewAnswerDTO answer) {
        return answer != null
                && !clean(answer.getAnswerText()).isBlank()
                && Objects.requireNonNullElse(answer.getScore(), 0) <= 2;
    }

    private List<MockInterviewStatsDTO.InsightDTO> insightsBy(List<MockInterviewSessionDTO> sessions, java.util.function.Function<MockInterviewSessionDTO, String> classifier) {
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }
        return sessions.stream()
                .filter(session -> blankToNull(clean(classifier.apply(session))) != null)
                .collect(Collectors.groupingBy(session -> blankToNull(clean(classifier.apply(session))), LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> toInsight(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(MockInterviewStatsDTO.InsightDTO::getSessionCount).reversed()
                        .thenComparing(MockInterviewStatsDTO.InsightDTO::getAverageScorePercent, Comparator.reverseOrder())
                        .thenComparing(MockInterviewStatsDTO.InsightDTO::getName))
                .limit(5)
                .toList();
    }

    private MockInterviewStatsDTO.InsightDTO toInsight(String name, List<MockInterviewSessionDTO> sessions) {
        int sessionCount = sessions.size();
        int averageScore = sessions.stream().mapToInt(this::scorePercent).sum() / Math.max(1, sessionCount);
        int bestScore = sessions.stream().mapToInt(this::scorePercent).max().orElse(0);
        int averageDuration = sessions.stream().mapToInt(session -> Math.max(0, Objects.requireNonNullElse(session.getDurationSeconds(), 0))).sum() / Math.max(1, sessionCount);
        return MockInterviewStatsDTO.InsightDTO.builder()
                .name(name)
                .sessionCount(sessionCount)
                .averageScorePercent(averageScore)
                .bestScorePercent(bestScore)
                .averageDurationSeconds(averageDuration)
                .build();
    }

    private int scorePercent(MockInterviewSessionDTO session) {
        int totalScore = Math.max(0, Objects.requireNonNullElse(session.getTotalScore(), 0));
        int questionCount = Math.max(1, Objects.requireNonNullElse(session.getQuestionCount(), 0));
        return Math.min(100, Math.round(totalScore * 100.0f / (questionCount * 5)));
    }

    private String normalizeDifficulty(String value) {
        String cleaned = clean(value);
        return List.of("easy", "medium", "hard").contains(cleaned) ? cleaned : "";
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private int number(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }
}
