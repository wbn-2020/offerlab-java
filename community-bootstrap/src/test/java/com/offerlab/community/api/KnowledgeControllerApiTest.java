package com.offerlab.community.api;

import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.post.api.dto.KnowledgeRelationEdgeDTO;
import com.offerlab.community.post.api.dto.KnowledgeRelationGraphDTO;
import com.offerlab.community.post.api.dto.KnowledgeRelationNodeDTO;
import com.offerlab.community.post.application.ContentSeriesService;
import com.offerlab.community.post.application.DiscoveryMapService;
import com.offerlab.community.post.application.KnowledgeRelationService;
import com.offerlab.community.post.controller.KnowledgeController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class KnowledgeControllerApiTest {

    @Mock
    private KnowledgeRelationService knowledgeRelationService;
    @Mock
    private DiscoveryMapService discoveryMapService;
    @Mock
    private ContentSeriesService contentSeriesService;
    @Mock
    private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ApiTestSupport.mvc(new KnowledgeController(
                knowledgeRelationService,
                discoveryMapService,
                contentSeriesService
        ), jwtService);
    }

    @Test
    void publicKnowledgeRelationsExposeLightweightNodesAndEdges() throws Exception {
        when(knowledgeRelationService.explore(null, null, null, 1, 6)).thenReturn(KnowledgeRelationGraphDTO.builder()
                .limit(6)
                .nodes(List.of(
                        KnowledgeRelationNodeDTO.builder().key("domain:1").type("domain").label("tech").build(),
                        KnowledgeRelationNodeDTO.builder().key("post:101").type("post").label("Java guide").build()))
                .edges(List.of(
                        KnowledgeRelationEdgeDTO.builder()
                                .source("domain:1")
                                .target("post:101")
                                .relation("domain_post")
                                .weight(1)
                                .build()))
                .build());

        mvc.perform(get("/api/v1/knowledge/relations")
                        .param("domain", "1")
                        .param("limit", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.limit").value(6))
                .andExpect(jsonPath("$.data.nodes[0].key").value("domain:1"))
                .andExpect(jsonPath("$.data.edges[0].relation").value("domain_post"));

        verify(knowledgeRelationService).explore(null, null, null, 1, 6);
    }

    @Test
    void publicKnowledgeRelationsUseOnlyDocumentedSeeds() throws Exception {
        when(knowledgeRelationService.explore(88L, 22L, 33L, 1, 6)).thenReturn(KnowledgeRelationGraphDTO.builder()
                .limit(6)
                .nodes(List.of(KnowledgeRelationNodeDTO.builder().key("domain:1").type("domain").label("tech").build()))
                .edges(List.of())
                .build());

        mvc.perform(get("/api/v1/knowledge/relations")
                        .param("postId", "88")
                        .param("tagId", "22")
                        .param("topicId", "33")
                        .param("domain", "1")
                        .param("limit", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.nodes[0].key").value("domain:1"));

        verify(knowledgeRelationService).explore(88L, 22L, 33L, 1, 6);
    }
}
