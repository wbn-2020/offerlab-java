package com.offerlab.community.notification.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.BinaryMessage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificationPacketCodecTest {

    private final NotificationPacketCodec codec = new NotificationPacketCodec(new ObjectMapper());

    @Test
    void roundTripsFrontendCompatibleBigEndianPacket() {
        BinaryMessage encoded = codec.encode(NotificationPacketCommand.AUTH_REQ, Map.of("token", "jwt-value"));

        NotificationPacket decoded = codec.decode(encoded);

        assertEquals(NotificationPacketCommand.AUTH_REQ, decoded.command());
        assertEquals("jwt-value", decoded.body().path("token").textValue());
    }

    @Test
    void rejectsInvalidMagicAndDeclaredLength() {
        ByteBuffer invalidMagic = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN);
        invalidMagic.putInt(0x01020304).putInt(8).putShort((short) NotificationPacketCommand.PING.code()).put((byte) '{').put((byte) '}');
        invalidMagic.flip();
        assertThrows(NotificationProtocolException.class, () -> codec.decode(invalidMagic));

        ByteBuffer invalidLength = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN);
        invalidLength.putInt(NotificationPacketCodec.MAGIC).putInt(99)
                .putShort((short) NotificationPacketCommand.PING.code()).put((byte) '{').put((byte) '}');
        invalidLength.flip();
        assertThrows(NotificationProtocolException.class, () -> codec.decode(invalidLength));
    }

    @Test
    void rejectsOversizedAndUnknownCommandFrames() {
        ByteBuffer oversized = ByteBuffer.allocate(NotificationPacketCodec.MAX_FRAME_BYTES + 1);
        assertThrows(NotificationProtocolException.class, () -> codec.decode(oversized));

        ByteBuffer unknownCommand = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN);
        unknownCommand.putInt(NotificationPacketCodec.MAGIC).putInt(8).putShort((short) 0x7FFF).put((byte) '{').put((byte) '}');
        unknownCommand.flip();
        assertThrows(NotificationProtocolException.class, () -> codec.decode(unknownCommand));
    }
}
