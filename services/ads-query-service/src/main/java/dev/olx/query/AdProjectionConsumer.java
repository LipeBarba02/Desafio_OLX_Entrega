package dev.olx.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
public class AdProjectionConsumer {
    private static final Logger log=LoggerFactory.getLogger(AdProjectionConsumer.class);
    private final ObjectMapper json;private final OpenSearchClient search;private final StringRedisTemplate redis;
    public AdProjectionConsumer(ObjectMapper json,OpenSearchClient search,StringRedisTemplate redis){this.json=json;this.search=search;this.redis=redis;}
    @RetryableTopic(attempts="5",backoff=@Backoff(delay=1000,maxDelay=30000,multiplier=2.0,random=true),dltTopicSuffix=".dlt")
    @KafkaListener(topics="${app.kafka.topics.ad-moderation-decided}",groupId="ads-query")
    public void project(String payload)throws Exception{
        AdModerationDecidedV1 event=json.readValue(payload,AdModerationDecidedV1.class);
        if(event.status().equalsIgnoreCase("approved"))search.upsert(new SearchDocument(event.adId(),event.title(),event.description(),event.price(),event.category(),event.adCreatedAt()));
        else search.delete(event.adId());
        try{redis.delete(detailKey(event.adId()));}catch(DataAccessException unavailable){log.warn("Redis unavailable; detail cache will expire by TTL for {}",event.adId());}
    }
    @DltHandler public void deadLetter(String payload){
        String eventId="unparsed";try{eventId=json.readTree(payload).path("eventId").asText("unparsed");}catch(Exception ignored){ }
        log.error("Projection event reached DLT; inspect and replay from durable event source. eventId={}",eventId);
    }
    static String detailKey(UUID id){return "ads:detail:v1:"+id;}
}
