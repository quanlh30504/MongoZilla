package com.example.MongoZilla.Kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Producer {
    @Value("${spring.kafka.topic.index-sync}")
    private String topic;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishSyncMessage(String action, String collection, Document data) {
        try {
            ObjectNode json = objectMapper.createObjectNode();
            json.put("action", action);
            json.put("collection", collection);
            json.set("data", objectMapper.readTree(data.toJson()));

            kafkaTemplate.send(topic, json.toString());
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish index sync message", e);
        }
    }
}
