package dev.olx.ads.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.olx.ads.domain.*;
import dev.olx.ads.persistence.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AdSubmissionServiceTest {
    @Test void savesAdAndOutboxPayloadForSameSubmission(){
        AdRepository ads=mock(AdRepository.class);OutboxEventRepository outbox=mock(OutboxEventRepository.class);ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
        when(ads.save(any(AdEntity.class))).thenAnswer(call->call.getArgument(0));
        Clock clock=Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC);
        var service=new AdSubmissionService(ads,outbox,mapper,clock);
        var draft=new AdDraft("Notebook usado em bom estado","Descrição detalhada",new BigDecimal("100.00"),Category.ELECTRONICS,List.of("https://img.example/a.jpg"));
        var result=service.submit(draft);
        ArgumentCaptor<OutboxEventEntity> captured=ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(ads).save(any(AdEntity.class));verify(outbox).save(captured.capture());
        assertEquals(result.adId(),captured.getValue().getAggregateId());assertEquals("PENDING",captured.getValue().getStatus());
        JsonNode payload;try{payload=mapper.readTree(captured.getValue().getPayload());}catch(Exception e){throw new AssertionError(e);}
        assertEquals(result.adId().toString(),payload.path("adId").asText());assertEquals(1,payload.path("schemaVersion").asInt());
    }
}
