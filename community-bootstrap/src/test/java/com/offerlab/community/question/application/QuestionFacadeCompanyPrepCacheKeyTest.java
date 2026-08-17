package com.offerlab.community.question.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.post.api.PostFacade;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.question.infrastructure.persistence.mapper.CompanyAliasMapper;
import com.offerlab.community.question.infrastructure.persistence.mapper.InterviewQuestionMapper;
import com.offerlab.community.question.infrastructure.persistence.po.CompanyAliasPO;
import com.offerlab.community.question.infrastructure.persistence.po.InterviewQuestionPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionFacadeCompanyPrepCacheKeyTest {

    @Mock
    private InterviewQuestionMapper questionMapper;
    @Mock
    private CompanyAliasMapper companyAliasMapper;
    @Mock
    private PostMapper postMapper;
    @Mock
    private PostFacade postFacade;

    private QuestionFacadeImpl facade;

    @BeforeEach
    void setUp() {
        facade = new QuestionFacadeImpl(
                questionMapper,
                null,
                null,
                null,
                null,
                companyAliasMapper,
                null,
                postMapper,
                null,
                postFacade,
                null,
                null,
                new ObjectMapper(),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    @Test
    void includesRawAndCanonicalKeysFromSourcePostWithoutQuestionRecords() {
        when(questionMapper.selectByPostId(101L, true)).thenReturn(List.of());
        when(postMapper.selectExtJsonByPostId(101L))
                .thenReturn("{\"company\":\"Source Alias\"}");
        when(companyAliasMapper.findEnabledByName("Source Alias"))
                .thenReturn(alias("Source Canonical", "Source Alias"));

        List<String> keys = facade.resolveCompanyPrepCacheKeysForPosts(List.of(101L));

        assertEquals(List.of(
                "question:company-prep:Source Alias",
                "question:company-prep:Source Canonical"
        ), keys);
        verify(postMapper).selectExtJsonByPostId(101L);
        verifyNoInteractions(postFacade);
    }

    @Test
    void preservesQuestionKeysAndNormalizesSourcePostIdsExactlyOnce() {
        when(questionMapper.selectByPostId(102L, true)).thenReturn(List.of(question("Question Alias")));
        when(postMapper.selectExtJsonByPostId(102L))
                .thenReturn("{\"company\":\" Source Alias \"}");
        when(companyAliasMapper.findEnabledByName("Question Alias"))
                .thenReturn(alias("Question Canonical", "Question Alias"));
        when(companyAliasMapper.findEnabledByName("Source Alias"))
                .thenReturn(alias("Source Canonical", "Source Alias"));

        List<String> keys = facade.resolveCompanyPrepCacheKeysForPosts(
                Arrays.asList(null, -1L, 0L, 102L, 102L));

        assertEquals(List.of(
                "question:company-prep:Question Alias",
                "question:company-prep:Question Canonical",
                "question:company-prep:Source Alias",
                "question:company-prep:Source Canonical"
        ), keys);
        verify(questionMapper).selectByPostId(102L, true);
        verify(postMapper).selectExtJsonByPostId(102L);
        verifyNoInteractions(postFacade);
    }

    private static InterviewQuestionPO question(String company) {
        InterviewQuestionPO question = new InterviewQuestionPO();
        question.setCompany(company);
        return question;
    }

    private static CompanyAliasPO alias(String canonical, String alias) {
        CompanyAliasPO companyAlias = new CompanyAliasPO();
        companyAlias.setCanonicalCompany(canonical);
        companyAlias.setAlias(alias);
        companyAlias.setStatus(1);
        return companyAlias;
    }
}
