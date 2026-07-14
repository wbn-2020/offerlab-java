package com.offerlab.community.interaction.api.enums;

import java.util.EnumSet;
import java.util.Set;

public enum QuestionStatus {
    OPEN,
    ANSWERED,
    ACCEPTED,
    NO_RELIABLE_CONCLUSION,
    CLOSED,
    DUPLICATE;

    public boolean canTransitionTo(QuestionStatus target) {
        if (target == null) {
            return false;
        }
        if (this == target) {
            return true;
        }
        Set<QuestionStatus> allowed = switch (this) {
            case OPEN -> EnumSet.of(ANSWERED, NO_RELIABLE_CONCLUSION, CLOSED, DUPLICATE);
            case ANSWERED -> EnumSet.of(OPEN, NO_RELIABLE_CONCLUSION, CLOSED, DUPLICATE);
            case ACCEPTED -> EnumSet.of(OPEN, ANSWERED, NO_RELIABLE_CONCLUSION, CLOSED, DUPLICATE);
            case NO_RELIABLE_CONCLUSION -> EnumSet.of(OPEN, ANSWERED, CLOSED, DUPLICATE);
            case CLOSED -> EnumSet.of(OPEN, ANSWERED, DUPLICATE);
            case DUPLICATE -> EnumSet.of(OPEN, ANSWERED, NO_RELIABLE_CONCLUSION, CLOSED);
        };
        return allowed.contains(target);
    }
}
