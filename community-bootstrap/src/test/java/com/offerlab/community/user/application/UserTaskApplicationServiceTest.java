package com.offerlab.community.user.application;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.user.api.dto.UserTaskOverviewDTO;
import com.offerlab.community.user.domain.model.User;
import com.offerlab.community.user.domain.repository.UserRepository;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserTaskStateMapper;
import com.offerlab.community.user.infrastructure.persistence.po.UserTaskStatePO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserTaskApplicationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserTaskStateMapper taskStateMapper;
    @Mock
    private SnowflakeIdGenerator idGenerator;

    private UserTaskApplicationService service;

    @BeforeEach
    void setUp() {
        service = new UserTaskApplicationService(userRepository, taskStateMapper, idGenerator);
    }

    @Test
    void getOnboardingTasksReturnsExpectedDefinitionsForFreshUser() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder()
                .id(7L)
                .createTime(LocalDateTime.now().minusDays(1))
                .build()));
        when(taskStateMapper.selectByUserAndTaskDate(7L, "ONBOARDING", LocalDate.of(1970, 1, 1)))
                .thenReturn(List.of(completedState(7L, "ONBOARDING", "VIEW_PUBLIC_CONTENT", LocalDate.of(1970, 1, 1))));

        UserTaskOverviewDTO overview = service.getOnboardingTasks(7L);

        assertEquals("ONBOARDING", overview.getTaskType());
        assertEquals(4, overview.getTotalCount());
        assertEquals(1, overview.getCompletedCount());
        assertTrue(Boolean.TRUE.equals(overview.getActive()));
        assertEquals(List.of(
                "VIEW_PUBLIC_CONTENT",
                "FOLLOW_FIRST_USER",
                "INTERACT_ONCE",
                "PUBLISH_FIRST_POST"), overview.getItems().stream().map(item -> item.getTaskCode()).toList());
        assertTrue(Boolean.TRUE.equals(overview.getItems().get(0).getCompleted()));
        assertFalse(Boolean.TRUE.equals(overview.getItems().get(1).getCompleted()));
    }

    @Test
    void getOnboardingTasksMarksOldUsersInactiveAfterFirstWeek() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder()
                .id(7L)
                .createTime(LocalDateTime.now().minusDays(10))
                .build()));
        when(taskStateMapper.selectByUserAndTaskDate(7L, "ONBOARDING", LocalDate.of(1970, 1, 1)))
                .thenReturn(List.of());

        UserTaskOverviewDTO overview = service.getOnboardingTasks(7L);

        assertFalse(Boolean.TRUE.equals(overview.getActive()));
        assertEquals(0, overview.getCompletedCount());
    }

    @Test
    void completeDailyBrowseTaskUpsertsManualTaskState() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder()
                .id(7L)
                .createTime(LocalDateTime.now())
                .build()));
        when(idGenerator.nextId()).thenReturn(9001L);

        service.completeDailyTask(7L, "DAILY_VIEW_PUBLIC_CONTENT");

        ArgumentCaptor<UserTaskStatePO> captor = ArgumentCaptor.forClass(UserTaskStatePO.class);
        verify(taskStateMapper).upsertCompleted(captor.capture());
        UserTaskStatePO state = captor.getValue();
        assertEquals(7L, state.getUid());
        assertEquals("DAILY", state.getTaskType());
        assertEquals("DAILY_VIEW_PUBLIC_CONTENT", state.getTaskCode());
        assertEquals(LocalDate.now(), state.getTaskDate());
        assertEquals(1, state.getCompleted());
        assertEquals("manual.daily", state.getCompleteSource());
    }

    @Test
    void markInteractionCompletionUpsertsOnboardingAndDailyTasksTogether() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder()
                .id(7L)
                .createTime(LocalDateTime.now().minusDays(2))
                .build()));
        when(idGenerator.nextId()).thenReturn(9001L, 9002L);

        service.markInteractionCompleted(7L, "interaction.like", 88L);

        ArgumentCaptor<UserTaskStatePO> captor = ArgumentCaptor.forClass(UserTaskStatePO.class);
        verify(taskStateMapper, times(2)).upsertCompleted(captor.capture());
        List<UserTaskStatePO> states = captor.getAllValues();
        assertEquals(Set.of("INTERACT_ONCE", "DAILY_INTERACT_ONCE"),
                states.stream().map(UserTaskStatePO::getTaskCode).collect(Collectors.toSet()));
        assertTrue(states.stream().anyMatch(state ->
                "ONBOARDING".equals(state.getTaskType()) && LocalDate.of(1970, 1, 1).equals(state.getTaskDate())));
        assertTrue(states.stream().anyMatch(state ->
                "DAILY".equals(state.getTaskType()) && LocalDate.now().equals(state.getTaskDate())));
        assertTrue(states.stream().allMatch(state -> Long.valueOf(88L).equals(state.getCompleteRefId())));
    }

    @Test
    void completeOnboardingTaskRejectsAutoOnlyTaskCodes() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder()
                .id(7L)
                .createTime(LocalDateTime.now())
                .build()));

        assertThrows(BizException.class, () -> service.completeOnboardingTask(7L, "FOLLOW_FIRST_USER"));
    }

    private static UserTaskStatePO completedState(Long uid, String taskType, String taskCode, LocalDate taskDate) {
        UserTaskStatePO state = new UserTaskStatePO();
        state.setUid(uid);
        state.setTaskType(taskType);
        state.setTaskCode(taskCode);
        state.setTaskDate(taskDate);
        state.setCompleted(1);
        state.setCompleteSource("test");
        return state;
    }
}
