package com.offerlab.community.user.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.infra.mq.producer.EventPublisher;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.infra.security.PasswordEncoder;
import com.offerlab.community.infra.tx.AfterCommitExecutor;
import com.offerlab.community.user.api.dto.UserIntentDTO;
import com.offerlab.community.user.domain.model.User;
import com.offerlab.community.user.domain.repository.FollowRepository;
import com.offerlab.community.user.domain.repository.UserRepository;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserPrivacySettingMapper;
import com.offerlab.community.user.infrastructure.persistence.mapper.UserProfileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserApplicationServiceIntentSanitizationTest {

    @Mock
    private UserRepository userRepo;
    @Mock
    private FollowRepository followRepo;
    @Mock
    private SnowflakeIdGenerator idGen;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private UserPrivacySettingMapper privacySettingMapper;
    @Mock
    private UserProfileMapper profileMapper;
    @Mock
    private UserCacheService userCacheService;
    @Mock
    private ContentModerationService contentModerationService;
    @Mock
    private AfterCommitExecutor afterCommit;

    private UserApplicationService service;

    @BeforeEach
    void setUp() {
        service = new UserApplicationService(
                userRepo,
                followRepo,
                idGen,
                passwordEncoder,
                jwtService,
                redisTemplate,
                eventPublisher,
                objectMapper,
                privacySettingMapper,
                profileMapper,
                userCacheService,
                contentModerationService,
                afterCommit);
    }

    @Test
    void updateIntentSerializesSanitizedCopyInsteadOfRawPayload() throws Exception {
        User user = User.builder().id(7L).accountStatus(User.STATUS_NORMAL).build();
        when(userRepo.findById(7L)).thenReturn(Optional.of(user));
        when(objectMapper.writeValueAsString(any(UserIntentDTO.class))).thenReturn("{\"intent\":true}");

        UserIntentDTO raw = UserIntentDTO.builder()
                .targetCompanies(List.of(" OpenAI ", " ", "OpenAI", " Anthropic "))
                .targetPositions(List.of("  Backend Engineer ", "", "Backend Engineer"))
                .yearsOfExp(3)
                .expectedCity("  Shanghai  ")
                .techStack(List.of(" Java ", "Spring", "Java"))
                .interestTopics(List.of(" 职场成长 ", " ", "职场成长", "租房生活"))
                .interestTags(List.of(" 效率工具 ", "效率工具"))
                .contentPreferences(List.of(" ", "\t"))
                .expectedSalaryRange(UserIntentDTO.SalaryRange.builder()
                        .min(30)
                        .max(50)
                        .unit("  k/month  ")
                        .build())
                .build();

        service.updateIntent(7L, raw);

        ArgumentCaptor<UserIntentDTO> intentCaptor = ArgumentCaptor.forClass(UserIntentDTO.class);
        verify(objectMapper).writeValueAsString(intentCaptor.capture());
        UserIntentDTO normalized = intentCaptor.getValue();
        assertEquals(List.of("OpenAI", "Anthropic"), normalized.getTargetCompanies());
        assertEquals(List.of("Backend Engineer"), normalized.getTargetPositions());
        assertEquals("Shanghai", normalized.getExpectedCity());
        assertEquals(List.of("Java", "Spring"), normalized.getTechStack());
        assertEquals(List.of("职场成长", "租房生活"), normalized.getInterestTopics());
        assertEquals(List.of("效率工具"), normalized.getInterestTags());
        assertNull(normalized.getContentPreferences());
        assertEquals(30, normalized.getExpectedSalaryRange().getMin());
        assertEquals(50, normalized.getExpectedSalaryRange().getMax());
        assertEquals("k/month", normalized.getExpectedSalaryRange().getUnit());

        verify(userRepo).updateProfile(user);
        assertEquals("{\"intent\":true}", user.getIntentJson());
        verifyNoInteractions(userCacheService);
        ArgumentCaptor<Runnable> intentEvictionCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(afterCommit).execute(intentEvictionCaptor.capture(), eq("user intent cache eviction:7"));
        intentEvictionCaptor.getValue().run();
        verify(userCacheService).evictBrief(7L);

        clearInvocations(userRepo, userCacheService, afterCommit);

        service.updateProfile(7L, " Updated Nickname ", null, null);

        verify(userRepo).updateProfile(user);
        assertEquals("Updated Nickname", user.getNickname());
        verifyNoInteractions(userCacheService);
        ArgumentCaptor<Runnable> evictionCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(afterCommit).execute(evictionCaptor.capture(), eq("user profile cache eviction:7"));

        evictionCaptor.getValue().run();

        verify(userCacheService).evictBrief(7L);
    }
}
