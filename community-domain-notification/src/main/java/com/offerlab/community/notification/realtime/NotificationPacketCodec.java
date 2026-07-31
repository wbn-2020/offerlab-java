package com.offerlab.community.notification.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

@Component
public class NotificationPacketCodec {

    public static final int MAGIC = 0xCAFEBABE;
    public static final int MAX_FRAME_BYTES = 16 * 1024;

    private static final int MAGIC_BYTES = Integer.BYTES;
    private static final int LENGTH_BYTES = Integer.BYTES;
    private static final int COMMAND_BYTES = Short.BYTES;
    private static final int HEADER_BYTES = MAGIC_BYTES + LENGTH_BYTES + COMMAND_BYTES;
    private static final int MIN_DECLARED_LENGTH = LENGTH_BYTES + COMMAND_BYTES;

    private final ObjectMapper objectMapper;

    public NotificationPacketCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public NotificationPacket decode(BinaryMessage message) {
        if (message == null) {
            throw new NotificationProtocolException("missing binary message");
        }
        return decode(message.getPayload());
    }

    public NotificationPacket decode(ByteBuffer payload) {
        if (payload == null) {
            throw new NotificationProtocolException("missing binary payload");
        }
        ByteBuffer frame = payload.asReadOnlyBuffer().order(ByteOrder.BIG_ENDIAN);
        int frameLength = frame.remaining();
        if (frameLength < HEADER_BYTES) {
            throw new NotificationProtocolException("frame is too short");
        }
        if (frameLength > MAX_FRAME_BYTES) {
            throw new NotificationProtocolException("frame exceeds maximum size");
        }

        int magic = frame.getInt();
        if (magic != MAGIC) {
            throw new NotificationProtocolException("invalid magic");
        }

        int declaredLength = frame.getInt();
        if (declaredLength < MIN_DECLARED_LENGTH || declaredLength != frameLength - MAGIC_BYTES) {
            throw new NotificationProtocolException("invalid declared length");
        }

        NotificationPacketCommand command = NotificationPacketCommand.fromCode(Short.toUnsignedInt(frame.getShort()));
        byte[] bodyBytes = new byte[frame.remaining()];
        frame.get(bodyBytes);
        JsonNode body;
        try {
            body = objectMapper.readTree(bodyBytes);
        } catch (IOException e) {
            throw new NotificationProtocolException("invalid json body", e);
        }
        if (body == null) {
            throw new NotificationProtocolException("missing json body");
        }
        return new NotificationPacket(command, body);
    }

    public BinaryMessage encode(NotificationPacketCommand command, Object body) {
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        byte[] bodyBytes;
        try {
            bodyBytes = objectMapper.writeValueAsBytes(body == null ? Map.of() : body);
        } catch (IOException e) {
            throw new IllegalArgumentException("packet body cannot be serialized", e);
        }
        int frameLength = HEADER_BYTES + bodyBytes.length;
        if (frameLength > MAX_FRAME_BYTES) {
            throw new IllegalArgumentException("packet exceeds maximum frame size");
        }

        ByteBuffer frame = ByteBuffer.allocate(frameLength).order(ByteOrder.BIG_ENDIAN);
        frame.putInt(MAGIC);
        frame.putInt(LENGTH_BYTES + COMMAND_BYTES + bodyBytes.length);
        frame.putShort((short) command.code());
        frame.put(bodyBytes);
        return new BinaryMessage(frame.array());
    }
}
