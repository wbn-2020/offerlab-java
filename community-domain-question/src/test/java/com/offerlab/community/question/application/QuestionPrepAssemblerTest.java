package com.offerlab.community.question.application;

import com.offerlab.community.question.api.dto.CompanyPrepDTO;
import com.offerlab.community.question.api.dto.PrepTargetDTO;
import com.offerlab.community.question.api.dto.QuestionDTO;
import com.offerlab.community.question.infrastructure.persistence.po.UserQuestionProgressPO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionPrepAssemblerTest {
    private final QuestionPrepAssembler assembler = new QuestionPrepAssembler();

    @Test
    void anonymousChecklistDoesNotCompletePersonalTarget() {
        var progress = CompanyPrepDTO.UserPrepSummaryDTO.builder()
                .favoriteCount(0L).learningCount(0L).masteredCount(0L).reviewCount(0L).build();
        var checklist = assembler.companyPrepChecklist("ByteDance", null, true,
                List.of(), List.of(), List.of(), List.of(), progress);

        var target = checklist.stream().filter(item -> "target".equals(item.getKey())).findFirst().orElseThrow();

        assertFalse(target.getDone());
        assertEquals(0, target.getCurrent());
        assertTrue(target.getActionHref().startsWith("/login?redirect="));
        assertEquals(0, assembler.prepScore(checklist));
        assertEquals(3, assembler.nextActions(checklist).size());
    }

    @Test
    void checklistScoreUsesDoneItemsAndNextActionsUseIncompleteItems() {
        var progress = CompanyPrepDTO.UserPrepSummaryDTO.builder()
                .favoriteCount(2L).learningCount(1L).masteredCount(3L).reviewCount(0L).build();
        var questions = List.of(new QuestionDTO(), new QuestionDTO(), new QuestionDTO(), new QuestionDTO(), new QuestionDTO());
        var positions = List.of(new CompanyPrepDTO.NameCountDTO("Java", 4L));
        var tags = List.of(new CompanyPrepDTO.NameCountDTO("Spring", 3L),
                new CompanyPrepDTO.NameCountDTO("MySQL", 2L), new CompanyPrepDTO.NameCountDTO("Redis", 1L));

        var checklist = assembler.companyPrepChecklist("ByteDance", 1L, true, questions, List.of(), positions, tags, progress);

        assertEquals(5, checklist.stream().filter(CompanyPrepDTO.ChecklistItemDTO::getDone).count());
        assertEquals(83, assembler.prepScore(checklist));
        assertEquals(List.of(checklist.get(2).getTitle()), assembler.nextActions(checklist));
    }

    @Test
    void targetSummaryCountsProgress() {
        var favorite = progress(1, "learning");
        var mastered = progress(0, "mastered");
        var review = progress(0, "review");
        var target = PrepTargetDTO.builder().id(10L).targetType("company").targetValue("ByteDance").build();
        var recommended = List.of(QuestionDTO.builder().id(100L).build());

        var summary = assembler.targetSummary(target, 8, recommended, Map.of(1L, favorite, 2L, mastered, 3L, review));

        assertEquals(target, summary.target());
        assertEquals(8, summary.questionCount());
        assertEquals(1, summary.favoriteCount());
        assertEquals(1, summary.learningCount());
        assertEquals(1, summary.masteredCount());
        assertEquals(1, summary.reviewCount());
        assertEquals(recommended, summary.recommendedQuestions());
    }

    private UserQuestionProgressPO progress(int favorite, String status) {
        var progress = new UserQuestionProgressPO();
        progress.setFavorite(favorite);
        progress.setProgressStatus(status);
        return progress;
    }
}
