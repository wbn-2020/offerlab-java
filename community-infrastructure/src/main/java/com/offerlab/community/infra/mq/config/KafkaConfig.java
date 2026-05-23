package com.offerlab.community.infra.mq.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offerlab.community.infra.mq.EventEnvelope;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 配置
 * 生产者：acks=all, 幂等生产者, 微批处理
 * 消费者：手动提交, 批量消费, 并发处理
 */
@Configuration
@EnableKafka
@RequiredArgsConstructor
public class KafkaConfig {

    private final KafkaProperties kafkaProperties;
    private final ObjectMapper objectMapper;

    @Value("${offerlab.kafka.listener-concurrency:1}")
    private int listenerConcurrency;

    /**
     * 生产者工厂配置
     */
    @Bean
    public ProducerFactory<String, EventEnvelope<?>> producerFactory() {
        Map<String, Object> props = new HashMap<>();

        // 基础配置
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // 可靠性配置
        props.put(ProducerConfig.ACKS_CONFIG, "all");  // 所有 ISR 副本确认
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);  // 幂等生产者
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        // 性能配置
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);

        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * KafkaTemplate Bean
     */
    @Bean
    public KafkaTemplate<String, EventEnvelope<?>> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * 消费者工厂配置
     */
    @Bean
    public ConsumerFactory<String, EventEnvelope<?>> consumerFactory() {
        Map<String, Object> props = new HashMap<>();

        // 基础配置
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.getConsumer().getGroupId());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // 消费配置
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);  // 手动提交
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 200);

        // 会话配置
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3000);

        // 获取配置
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);

        // JSON 反序列化配置
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, EventEnvelope.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Kafka 监听容器工厂
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<?>> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<?>> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(listenerConcurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);  // 手动立即提交
        factory.setBatchListener(false);  // 单条消息处理
        return factory;
    }
}

