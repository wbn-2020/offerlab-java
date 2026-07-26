package com.offerlab.community.question.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionReviewStateMachineGuardTest {

    @Test
    void adminAndExtractionWritesMustShareVersionedPendingReviewState() throws Exception {
        String cmd = read("src/main/java/com/offerlab/community/question/api/dto/QuestionAdminUpdateCmd.java");
        String mapper = read("src/main/java/com/offerlab/community/question/infrastructure/persistence/mapper/InterviewQuestionMapper.java");
        String facade = read("src/main/java/com/offerlab/community/question/application/QuestionFacadeImpl.java");
        String handler = read("src/main/java/com/offerlab/community/question/application/QuestionPendingReviewQueueActionHandler.java");

        assertTrue(cmd.contains("private LocalDateTime expectedUpdateTime"),
                "admin edits must submit an explicit question version");
        assertTrue(mapper.contains("int updateAdminIfCurrent"),
                "admin edits must use a CAS mapper operation");
        assertTrue(mapper.contains("AND update_time = #{expectedUpdateTime}"),
                "question writes must compare the version read by the caller");
        assertTrue(mapper.contains("status = 0"),
                "admin content edits must return the question to pending review");
        assertTrue(mapper.contains("TIMESTAMPADD(MICROSECOND, 1000, update_time)"),
                "question versions must advance even when two writes land in the same millisecond");
        assertTrue(facade.contains("mergeItem.expectedUpdateTime()"),
                "question re-extraction must not overwrite a concurrent admin edit");
        assertTrue(facade.contains("ext.put(\"expectedUpdateTime\", question.getUpdateTime().toString())"),
                "review queue metadata must carry the persisted question version");
        assertTrue(handler.contains("questionFacade.resolvePendingQuestionReview"),
                "queue decisions must resolve the source question through its CAS path");
        assertTrue(handler.contains("resolveSourceBeforeQueue()"),
                "question source CAS must execute before queue terminalization");
        assertTrue(facade.contains("reviewQueuePublisher.resolveRequired(PENDING_REVIEW_SOURCE_TYPE"),
                "direct admin review must roll back its source CAS when queue closure fails");
        assertTrue(facade.contains("UserContext.get()"),
                "direct admin review must close claimed queue items as the current operator");
    }

    private String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
