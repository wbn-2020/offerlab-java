package com.offerlab.community.notification.application;

import com.offerlab.community.notification.api.NotificationFacade;
import com.offerlab.community.question.api.event.QuestionExtractionFinishedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionNotificationListener {
    private static final long TARGET_POST = 1L;

    private final NotificationFacade notificationFacade;

    @Async
    @EventListener
    public void onQuestionExtractionFinished(QuestionExtractionFinishedEvent event) {
        if (event == null || event.getPostAuthorUid() == null || event.getPostId() == null) {
            return;
        }
        notificationFacade.notifySystem(event.getPostAuthorUid(), TARGET_POST, event.getPostId(), content(event));
    }

    private Map<String, Object> content(QuestionExtractionFinishedEvent event) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("action", event.isSuccess() ? "question_extract_succeeded" : "question_extract_failed");
        content.put("postId", event.getPostId());
        content.put("taskId", event.getTaskId());
        content.put("postTitle", safeTitle(event.getPostTitle()));
        content.put("questionCount", Math.max(0, event.getQuestionCount()));
        if (!event.isSuccess() && event.getErrorMessage() != null && !event.getErrorMessage().isBlank()) {
            content.put("errorMessage", event.getErrorMessage());
        }
        return content;
    }

    private String safeTitle(String value) {
        String title = value == null ? "" : value.trim();
        return title.length() <= 80 ? title : title.substring(0, 80);
    }
}
