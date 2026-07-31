package com.offerlab.community.notification.realtime;

import java.util.Arrays;

public enum NotificationPacketCommand {
    AUTH_REQ(0x0001),
    AUTH_RESP(0x0002),
    NOTIF_PUSH(0x0003),
    UNREAD_COUNT(0x0004),
    PING(0x0005),
    PONG(0x0006);

    private final int code;

    NotificationPacketCommand(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static NotificationPacketCommand fromCode(int code) {
        return Arrays.stream(values())
                .filter(command -> command.code == code)
                .findFirst()
                .orElseThrow(() -> new NotificationProtocolException("unknown command"));
    }
}
