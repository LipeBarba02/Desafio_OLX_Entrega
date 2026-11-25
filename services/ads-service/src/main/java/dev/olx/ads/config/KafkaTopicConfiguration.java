package dev.olx.ads.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfiguration {
    @Bean NewTopic adSubmitted(@Value("${app.kafka.topics.ad-submitted:ads.submitted.v1}") String name,
                               @Value("${app.kafka.partitions:3}") int partitions,
                               @Value("${app.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }
    @Bean NewTopic moderationDecided(@Value("${app.kafka.topics.ad-moderation-decided:ads.moderation-decided.v1}") String name,
                                     @Value("${app.kafka.partitions:3}") int partitions,
                                     @Value("${app.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(name).partitions(partitions).replicas(replicas).build();
    }
}
