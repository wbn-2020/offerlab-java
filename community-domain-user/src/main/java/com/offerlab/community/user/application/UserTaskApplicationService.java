package com.offerlab.community.user.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.user.api.dto.UserTaskItemDTO;
import com.offerlab.community.user.api.dto.UserTaskOverviewDTO;
import com.offerlab.community.user.domain.model.User;
import com.offerlab.community.user.domain.repository.UserRepository;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserTaskStateMapper;
import com.offerlab.community.user.infrastructure.persistence.po.UserTaskStatePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserTaskApplicationService {

    static final String TASK_TYPE_ONBOARDING = "ONBOARDING";
    static final String TASK_TYPE_DAILY = "DAILY";
    static final LocalDate ONBOARDING_TASK_DATE = LocalDate.of(1970, 1, 1);
    static final int ONBOARDING_WINDOW_DAYS = 7;

    static final String VIEW_PUBLIC_CONTENT = "VIEW_PUBLIC_CONTENT";
    static final String FOLLOW_FIRST_USER = "FOLLOW_FIRST_USER";
    static final String INTERACT_ONCE = "INTERACT_ONCE";
    static final String PUBLISH_FIRST_POST = "PUBLISH_FIRST_POST";
    static final String DAILY_VIEW_PUBLIC_CONTENT = "DAILY_VIEW_PUBLIC_CONTENT";
    static final String DAILY_INTERACT_ONCE = "DAILY_INTERACT_ONCE";
    static final String DAILY_PUBLISH_ONCE = "DAILY_PUBLISH_ONCE";

    private final UserRepository userRepository;
    private final UserTaskStateMapper taskStateMapper;
    private final SnowflakeIdGenerator idGenerator;

    public UserTaskOverviewDTO getOnboardingTasks(Long uid) {
        User user = getUser(uid);
        Map<String, UserTaskStatePO> states = taskStateMapper.selectByUserAndTaskDate(uid, TASK_TYPE_ONBOARDING, ONBOARDING_TASK_DATE)
                .stream()
                .collect(Collectors.toMap(UserTaskStatePO::getTaskCode, Function.identity(), (left, right) -> left));
        return buildOverview(
                TASK_TYPE_ONBOARDING,
                "新人任务链",
                "先完成浏览、关注、互动和首次发布，尽快进入社区节奏。",
                isOnboardingActive(user),
                onboardingDefinitions(),
                states);
    }

    public UserTaskOverviewDTO getDailyTasks(Long uid) {
        getUser(uid);
        LocalDate today = LocalDate.now();
        Map<String, UserTaskStatePO> states = taskStateMapper.selectByUserAndTaskDate(uid, TASK_TYPE_DAILY, today)
                .stream()
                .collect(Collectors.toMap(UserTaskStatePO::getTaskCode, Function.identity(), (left, right) -> left));
        return buildOverview(
                TASK_TYPE_DAILY,
                "每日任务 Lite",
                "每天完成一次浏览、互动或发布，让回访更有方向。",
                true,
                dailyDefinitions(),
                states);
    }

    public void completeOnboardingTask(Long uid, String taskCode) {
        User user = getUser(uid);
        TaskDefinition definition = requireTask(onboardingDefinitions(), taskCode);
        if (!definition.manualCompletable() || !isOnboardingActive(user)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        upsertCompletion(uid, TASK_TYPE_ONBOARDING, taskCode, ONBOARDING_TASK_DATE, "manual.onboarding", null);
    }

    public void completeDailyTask(Long uid, String taskCode) {
        getUser(uid);
        TaskDefinition definition = requireTask(dailyDefinitions(), taskCode);
        if (!definition.manualCompletable()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        upsertCompletion(uid, TASK_TYPE_DAILY, taskCode, LocalDate.now(), "manual.daily", null);
    }

    public void markFollowCompleted(Long uid, Long followeeId) {
        User user = findUser(uid);
        if (user == null || !isOnboardingActive(user)) {
            return;
        }
        upsertCompletion(uid, TASK_TYPE_ONBOARDING, FOLLOW_FIRST_USER, ONBOARDING_TASK_DATE, "user.follow", followeeId);
    }

    public void markInteractionCompleted(Long uid, String source, Long refId) {
        User user = findUser(uid);
        if (user == null) {
            return;
        }
        if (isOnboardingActive(user)) {
            upsertCompletion(uid, TASK_TYPE_ONBOARDING, INTERACT_ONCE, ONBOARDING_TASK_DATE, source, refId);
        }
        upsertCompletion(uid, TASK_TYPE_DAILY, DAILY_INTERACT_ONCE, LocalDate.now(), source, refId);
    }

    public void markPublishCompleted(Long uid, Long postId) {
        User user = findUser(uid);
        if (user == null) {
            return;
        }
        if (isOnboardingActive(user)) {
            upsertCompletion(uid, TASK_TYPE_ONBOARDING, PUBLISH_FIRST_POST, ONBOARDING_TASK_DATE, "post.publish", postId);
        }
        upsertCompletion(uid, TASK_TYPE_DAILY, DAILY_PUBLISH_ONCE, LocalDate.now(), "post.publish", postId);
    }

    private UserTaskOverviewDTO buildOverview(String taskType,
                                              String title,
                                              String subtitle,
                                              boolean active,
                                              List<TaskDefinition> definitions,
                                              Map<String, UserTaskStatePO> states) {
        List<UserTaskItemDTO> items = definitions.stream()
                .map(definition -> toTaskItem(definition, states.get(definition.taskCode())))
                .toList();
        int completedCount = (int) items.stream().filter(item -> Boolean.TRUE.equals(item.getCompleted())).count();
        return UserTaskOverviewDTO.builder()
                .taskType(taskType)
                .title(title)
                .subtitle(subtitle)
                .active(active)
                .completedCount(completedCount)
                .totalCount(items.size())
                .items(items)
                .build();
    }

    private UserTaskItemDTO toTaskItem(TaskDefinition definition, UserTaskStatePO state) {
        return UserTaskItemDTO.builder()
                .taskCode(definition.taskCode())
                .title(definition.title())
                .description(definition.description())
                .actionText(definition.actionText())
                .actionRoute(definition.actionRoute())
                .manualCompletable(definition.manualCompletable())
                .completed(state != null && Objects.equals(state.getCompleted(), 1))
                .completedAt(state == null ? null : state.getFirstCompletedTime())
                .build();
    }

    private void upsertCompletion(Long uid,
                                  String taskType,
                                  String taskCode,
                                  LocalDate taskDate,
                                  String source,
                                  Long refId) {
        UserTaskStatePO state = new UserTaskStatePO();
        state.setId(idGenerator.nextId());
        state.setUid(uid);
        state.setTaskType(taskType);
        state.setTaskCode(taskCode);
        state.setTaskDate(taskDate);
        state.setCompleted(1);
        state.setCompleteSource(source);
        state.setCompleteRefId(refId);
        state.setFirstCompletedTime(LocalDateTime.now());
        taskStateMapper.upsertCompleted(state);
    }

    private TaskDefinition requireTask(List<TaskDefinition> definitions, String taskCode) {
        return definitions.stream()
                .filter(definition -> definition.taskCode().equals(taskCode))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_ERROR));
    }

    private User getUser(Long uid) {
        return userRepository.findById(uid).orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
    }

    private User findUser(Long uid) {
        return userRepository.findById(uid).orElse(null);
    }

    private boolean isOnboardingActive(User user) {
        if (user == null || user.getCreateTime() == null) {
            return true;
        }
        return !user.getCreateTime().isBefore(LocalDateTime.now().minusDays(ONBOARDING_WINDOW_DAYS));
    }

    private List<TaskDefinition> onboardingDefinitions() {
        return List.of(
                new TaskDefinition(VIEW_PUBLIC_CONTENT, "浏览一篇公开内容", "先打开一篇公开帖子，熟悉社区内容结构。", "去发现", "/explore", true),
                new TaskDefinition(FOLLOW_FIRST_USER, "关注一位作者", "找到同路人，后续更容易收到熟悉作者的新内容。", "去发现", "/explore", false),
                new TaskDefinition(INTERACT_ONCE, "完成一次互动", "点赞、收藏或评论任意一篇公开内容即可完成。", "去互动", "/explore", false),
                new TaskDefinition(PUBLISH_FIRST_POST, "发布第一篇内容", "发布你的第一篇经验、复盘或问题，建立自己的实践主页。", "去发布", "/editor", false)
        );
    }

    private List<TaskDefinition> dailyDefinitions() {
        return List.of(
                new TaskDefinition(DAILY_VIEW_PUBLIC_CONTENT, "今日浏览一次", "回到首页或发现页，继续浏览一篇公开内容。", "去首页", "/", true),
                new TaskDefinition(DAILY_INTERACT_ONCE, "今日完成一次互动", "点赞、收藏或评论一次，保持和社区的连接。", "去互动", "/explore", false),
                new TaskDefinition(DAILY_PUBLISH_ONCE, "今日发布一篇内容", "发布新的复盘、经验或问题，持续沉淀内容。", "去发布", "/editor", false)
        );
    }

    private record TaskDefinition(String taskCode,
                                  String title,
                                  String description,
                                  String actionText,
                                  String actionRoute,
                                  boolean manualCompletable) {
    }
}
