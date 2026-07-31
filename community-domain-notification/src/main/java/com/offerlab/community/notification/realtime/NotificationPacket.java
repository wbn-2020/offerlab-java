package com.offerlab.community.notification.realtime;

import com.fasterxml.jackson.databind.JsonNode;

public record NotificationPacket(NotificationPacketCommand command, JsonNode body) {
}
