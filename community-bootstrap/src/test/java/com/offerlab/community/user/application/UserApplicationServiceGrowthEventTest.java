package com.offerlab.community.user.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.id.SnowflakeIdGenerator;
import com.offerlab.community.infra.moderation.ContentModerationService;
import com.offerlab.community.infra.mq.producer.EventPublisher;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.infra.security.PasswordEncoder;
import com.offerlab.community.user.api.event.UserRegisteredEvent;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserApplicationServiceGrowthEventTest {

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
                contentModerationService);
    }

    @Test
    void registerPublishesUserRegisteredEventForGrowthClosure() {
        when(userRepo.findByEmail("new@offerlab.dev")).thenReturn(Optional.empty());
        when(idGen.nextId()).thenReturn(1001L);
        when(passwordEncoder.encode("secret123")).thenReturn("encoded-password");

        Long uid = service.register("new@offerlab.dev", "secret123", "newbie");

        assertEquals(1001L, uid);
        verify(userRepo).register(any());
        ArgumentCaptor<UserRegisteredEvent> captor = ArgumentCaptor.forClass(UserRegisteredEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertEquals(1001L, captor.getValue().getUid());
    }
}
