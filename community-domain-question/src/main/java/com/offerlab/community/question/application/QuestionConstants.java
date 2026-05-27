package com.offerlab.community.question.application;

public final class QuestionConstants {
    private QuestionConstants() {
    }

    public static final String TASK_TYPE_QUESTION_EXTRACT = "question_extract";

    public static final int TASK_PENDING = 0;
    public static final int TASK_RUNNING = 1;
    public static final int TASK_SUCCEEDED = 2;
    public static final int TASK_FAILED = 3;

    public static final int QUESTION_PENDING = 0;
    public static final int QUESTION_APPROVED = 1;
    public static final int QUESTION_HIDDEN = 2;
}
