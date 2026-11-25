package dev.olx.ads.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.olx.ads.domain.AdStatus;
import dev.olx.ads.persistence.AdRepository;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdModerationListener {
    private static final Logger log=LoggerFactory.getLogger(AdModerationListener.class);
    private final ObjectMapper json; private final AdRepository ads;
    public AdModerationListener(ObjectMapper json,AdRepository ads){this.json=json;this.ads=ads;}
    @RetryableTopic(attempts="4",backoff=@Backoff(delay=1000,maxDelay=30000,multiplier=2.0,random=true),dltTopicSuffix=".dlt")
    @KafkaListener(topics="${app.kafka.topics.ad-moderation-decided:ads.moderation-decided.v1}",groupId="ads-command")
    @Transactional
    public void onDecision(String payload) throws IOException {
        AdModerationDecidedV1 event=json.readValue(payload,AdModerationDecidedV1.class);
        var ad=ads.findById(event.adId()).orElse(null);
        if(ad==null)return; // stale/orphan event is acknowledged; alerting/ops policy is documented.
        AdStatus status=switch(event.status().toLowerCase()) {case "approved"->AdStatus.APPROVED;case "rejected"->AdStatus.REJECTED;default->throw new IllegalArgumentException("Unknown moderation status");};
        ad.applyModeration(status,event.rejectionReason()); // setting same decision twice is state-idempotent.
    }
    @DltHandler public void onDeadLetter(String payload){String eventId="unparsed";try{eventId=json.readTree(payload).path("eventId").asText("unparsed");}catch(Exception ignored){ }log.error("Ad status event reached DLT; ad may remain pending. eventId={}",eventId);}
}
