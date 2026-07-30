package com.offerlab.community.infra.mq.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.mq.EventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.SerializationUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaConfigTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void invalidJsonIsCapturedAsAValueDeserializationFailure() {
        KafkaConfig config = config();
        DefaultKafkaConsumerFactory<String, EventEnvelope<?>> factory =
                (DefaultKafkaConsumerFactory<String, EventEnvelope<?>>) config.consumerFactory();
        Map<String, Object> properties = factory.getConfigurationProperties();

        assertEquals(ErrorHandlingDeserializer.class,
                properties.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG));
        assertEquals(ErrorHandlingDeserializer.class,
                properties.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
        assertEquals(JsonDeserializer.class,
                properties.get(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS));

        ErrorHandlingDeserializer<EventEnvelope<?>> deserializer = new ErrorHandlingDeserializer<>();
        deserializer.configure(properties, false);
        RecordHeaders headers = new RecordHeaders();

        EventEnvelope<?> result = deserializer.deserialize(
                "post.published",
                headers,
                "{invalid-json".getBytes(StandardCharsets.UTF_8)
        );

        assertNull(result);
        assertNotNull(headers.lastHeader(SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER));
        deserializer.close();
    }

    @Test
    void deadLetterSerializersPreservePoisonBytesAndHandleNormalEvents() {
        byte[] poison = new byte[]{0, 1, 2, 3};
        Serializer<Object> keySerializer = KafkaConfig.deadLetterKeySerializer(objectMapper);
        Serializer<Object> valueSerializer = KafkaConfig.deadLetterValueSerializer(objectMapper);

        assertArrayEquals(poison, keySerializer.serialize("events.DLT", poison));
        assertArrayEquals(poison, valueSerializer.serialize("events.DLT", poison));

        EventEnvelope<String> event = EventEnvelope.<String>builder()
                .messageId("m1")
                .eventType("TEST")
                .payload("ok")
                .build();
        String json = new String(valueSerializer.serialize("events.DLT", event), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"messageId\":\"m1\""));
    }

    @Test
    void postSearchDltFactoryUsesTheSharedConsumerContract() {
        KafkaConfig config = config();

        ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<?>> factory =
                config.postSearchDeadLetterKafkaListenerContainerFactory();

        assertNotNull(factory.getConsumerFactory());
        assertEquals(ContainerProperties.AckMode.MANUAL_IMMEDIATE,
                factory.getContainerProperties().getAckMode());
    }

    private KafkaConfig config() {
        KafkaProperties properties = new KafkaProperties();
        properties.setBootstrapServers(List.of("localhost:9092"));
        properties.getConsumer().setGroupId("offerlab-test");
        return new KafkaConfig(properties, objectMapper);
    }
}
