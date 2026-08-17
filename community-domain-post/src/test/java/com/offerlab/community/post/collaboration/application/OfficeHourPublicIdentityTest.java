package com.offerlab.community.post.collaboration.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.post.collaboration.api.CollaborationModels.OfficeHourDTO;
import com.offerlab.community.post.collaboration.api.CollaborationModels.OfficeHourFeedbackDTO;
import com.offerlab.community.post.collaboration.api.CollaborationModels.OfficeHourReservationDTO;
import com.offerlab.community.post.collaboration.api.CollaborationModels.PublicActorDTO;
import com.offerlab.community.post.application.DomainModeratorService;
import com.offerlab.community.post.collaboration.infrastructure.persistence.CollaborationMapper;
import com.offerlab.community.post.collaboration.infrastructure.persistence.CollaborationRows.OfficeHourReservationRow;
import com.offerlab.community.post.collaboration.infrastructure.persistence.CollaborationRows.OfficeHourRow;
import com.offerlab.community.user.api.UserFacade;
import com.offerlab.community.user.api.dto.UserBriefDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfficeHourPublicIdentityTest {

    @Mock
    private CollaborationMapper mapper;
    @Mock
    private UserFacade userFacade;
    @Mock
    private DomainModeratorService domainModeratorService;

    private CollaborationService service;

    @BeforeEach
    void setUp() {
        service = new CollaborationService(
                mapper, null, null, null, null, domainModeratorService, null, null, userFacade);
    }

    @Test
    void publicOfficeHourContractsContainNoDatabaseIdentityFields() throws Exception {
        Set<String> forbidden = Set.of(
                "hostUid", "attendeeUid", "authorUid", "targetUid", "decidedBy", "cancelledBy", "uid");

        for (Class<?> type : List.of(
                PublicActorDTO.class,
                OfficeHourDTO.class,
                OfficeHourReservationDTO.class,
                OfficeHourFeedbackDTO.class)) {
            Set<String> fields = Arrays.stream(type.getDeclaredFields())
                    .map(Field::getName)
                    .collect(Collectors.toSet());
            assertTrue(fields.stream().noneMatch(forbidden::contains),
                    () -> type.getSimpleName() + " exposes a database identity field: " + fields);
        }

        String payload = new ObjectMapper().writeValueAsString(OfficeHourReservationDTO.builder()
                .id(100L)
                .officeHourId(200L)
                .host(PublicActorDTO.builder().displayName("主持人").self(false).badges(List.of()).build())
                .attendee(PublicActorDTO.builder().displayName("你").self(true).badges(List.of()).build())
                .viewerRole("ATTENDEE")
                .build());
        for (String field : forbidden) {
            assertFalse(payload.contains("\"" + field + "\""), "serialized payload leaks " + field);
        }
    }

    @Test
    void officeHourPageBatchLoadsActorsAndUsesSafeFallbacks() {
        allowSchema();
        OfficeHourRow knownHost = officeHour(200L, 11L, "已知主持人");
        OfficeHourRow missingHost = officeHour(199L, 12L, "缺失主持人");
        when(mapper.listOfficeHours(null, "OPEN", 11L, 0L, 3))
                .thenReturn(List.of(knownHost, missingHost));
        when(userFacade.batchGetUserBriefs(any())).thenReturn(Map.of(
                11L, UserBriefDTO.builder().uid(11L).nickname("不会展示的本人昵称").avatarUrl("/a.png").build()));

        PageResult<OfficeHourDTO> result = service.listOfficeHours(null, "OPEN", 11L, 0L, 2);

        assertEquals(2, result.getItems().size());
        assertEquals("你", result.getItems().get(0).getHost().getDisplayName());
        assertTrue(result.getItems().get(0).getHost().getSelf());
        assertEquals("/a.png", result.getItems().get(0).getHost().getAvatarUrl());
        assertEquals("社区成员", result.getItems().get(1).getHost().getDisplayName());
        assertFalse(result.getItems().get(1).getHost().getSelf());

        ArgumentCaptor<Collection<Long>> ids = collectionCaptor();
        verify(userFacade).batchGetUserBriefs(ids.capture());
        assertEquals(Set.of(11L, 12L), Set.copyOf(ids.getValue()));
    }

    @Test
    void reservationPageUsesOneActorBatchAndViewerRoleInsteadOfUidComparison() {
        allowSchema();
        OfficeHourRow officeHour = officeHour(200L, 11L, "时段");
        when(mapper.lockOfficeHour(200L)).thenReturn(officeHour);
        when(mapper.listOfficeHourReservations(200L, null, null, 0L, 3))
                .thenReturn(List.of(
                        reservation(301L, 200L, 11L, 21L),
                        reservation(300L, 200L, 11L, 22L)));
        when(userFacade.batchGetUserBriefs(any())).thenReturn(Map.of(
                11L, UserBriefDTO.builder().uid(11L).nickname("主持人甲").build(),
                21L, UserBriefDTO.builder().uid(21L).nickname("参与者乙").build(),
                22L, UserBriefDTO.builder().uid(22L).nickname("参与者丙").build()));

        PageResult<OfficeHourReservationDTO> result =
                service.listOfficeHourReservations(200L, null, 11L, 0L, 2);

        assertEquals(2, result.getItems().size());
        for (OfficeHourReservationDTO reservation : result.getItems()) {
            assertEquals("HOST", reservation.getViewerRole());
            assertEquals("你", reservation.getHost().getDisplayName());
            assertNotNull(reservation.getAttendee().getDisplayName());
        }

        ArgumentCaptor<Collection<Long>> ids = collectionCaptor();
        verify(userFacade).batchGetUserBriefs(ids.capture());
        assertEquals(Set.of(11L, 21L, 22L), Set.copyOf(ids.getValue()));
    }

    @Test
    void nullBatchResultFallsBackWithoutBreakingThePublicPage() {
        allowSchema();
        when(mapper.listOfficeHours(eq(null), eq("OPEN"), eq(null), eq(0L), eq(2)))
                .thenReturn(List.of(officeHour(200L, 11L, "时段")));
        when(userFacade.batchGetUserBriefs(any())).thenReturn(null);

        OfficeHourDTO result = service.listOfficeHours(null, "OPEN", null, 0L, 1)
                .getItems().get(0);

        assertEquals("社区成员", result.getHost().getDisplayName());
        assertEquals("", result.getHost().getAvatarUrl());
        assertFalse(result.getHost().getSelf());
    }

    private void allowSchema() {
        when(mapper.existingTableCount()).thenReturn(20);
        when(mapper.existingCriticalColumnCount()).thenReturn(60);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Collection<Long>> collectionCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Collection.class);
    }

    private static OfficeHourRow officeHour(Long id, Long hostUid, String title) {
        OfficeHourRow row = new OfficeHourRow();
        row.setId(id);
        row.setHostUid(hostUid);
        row.setDomain(3);
        row.setTitle(title);
        row.setDescription("公开经验交流");
        row.setStartsAt(LocalDateTime.now().plusDays(1));
        row.setEndsAt(LocalDateTime.now().plusDays(1).plusHours(1));
        row.setCapacity(5);
        row.setReservedCount(0);
        row.setStatus("OPEN");
        row.setHidden(0);
        return row;
    }

    private static OfficeHourReservationRow reservation(
            Long id, Long officeHourId, Long hostUid, Long attendeeUid) {
        OfficeHourReservationRow row = new OfficeHourReservationRow();
        row.setId(id);
        row.setOfficeHourId(officeHourId);
        row.setHostUid(hostUid);
        row.setAttendeeUid(attendeeUid);
        row.setTopic("预约主题 " + id);
        row.setStatus("PENDING");
        row.setHidden(0);
        return row;
    }
}
