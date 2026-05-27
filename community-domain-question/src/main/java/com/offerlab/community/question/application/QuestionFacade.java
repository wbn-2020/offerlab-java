package com.offerlab.community.question.application;

import com.offerlab.community.common.result.PageResult;
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
import com.offerlab.community.question.api.dto.UserPrepOverviewDTO;
import com.offerlab.community.post.api.dto.PostDTO;
import com.offerlab.community.post.api.dto.PostBriefDTO;

import java.util.List;
import java.util.Map;

public interface QuestionFacade {
    Long extractPostQuestions(Long postId, boolean manual);

    void processExtractTask(Long taskId);

    Map<String, Object> rebuildQuestions(int limit);

    Map<String, Object> rebuildQuestionIndex();

    List<AiTaskDTO> listTasks(Integer status, int limit);

    AiTaskDTO retryTask(Long taskId);

    PostQuestionBlockDTO getPostQuestionBlock(Long postId, Long viewerUid, boolean admin);

    PageResult<QuestionDTO> searchQuestions(QuestionQuery query, Long viewerUid);

    QuestionDetailDTO getQuestionDetail(Long questionId, Long viewerUid, boolean admin);

    List<PostBriefDTO> getRelatedPosts(Long questionId);

    Map<String, Object> favorite(Long questionId, Long uid, boolean favorite);

    Map<String, Object> updateProgress(Long questionId, Long uid, String status);

    List<String> suggestCompanies(String prefix, int size);

    CompanyPrepDTO getCompanyPrep(String company, Long viewerUid);

    UserPrepOverviewDTO getMyPrepOverview(Long uid);

    List<PrepTargetDTO> listPrepTargets(Long uid);

    PrepTargetDTO addPrepTarget(Long uid, PrepTargetCmd cmd);

    Map<String, Object> deletePrepTarget(Long uid, Long targetId);

    List<QuestionDTO> listAdminQuestions(Integer status, int limit);

    PageResult<QuestionDTO> pageAdminQuestions(Integer status, int page, int pageSize);

    Map<String, Long> questionAdminSummary();

    QuestionDTO updateQuestionAdmin(Long questionId, QuestionAdminUpdateCmd cmd);

    Map<String, Object> reviewQuestion(Long questionId, int status);

    List<CompanyAliasDTO> listCompanyAliases(String keyword, int limit);

    CompanyAliasDTO saveCompanyAlias(Long id, CompanyAliasCmd cmd);

    Map<String, Object> updateCompanyAliasStatus(Long id, int status);

    void evictQuestionCachesForPost(PostDTO post);
}
