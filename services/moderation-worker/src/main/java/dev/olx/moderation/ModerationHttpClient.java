package dev.olx.moderation;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ModerationHttpClient {
    private final RestClient client;
    public ModerationHttpClient(@Value("${moderation.base-url}") String baseUrl,
                                @Value("${moderation.connect-timeout-ms:1000}") int connectMs,
                                @Value("${moderation.read-timeout-ms:5000}") int readMs){
        SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(connectMs);factory.setReadTimeout(readMs);
        client=RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
    public Decision check(AdSubmittedV1 ad){
        Response result=client.post().uri("/v1/moderation/check")
            // Official mock contract accepts these four fields. Images are not sent or visually checked.
            .body(new Request(ad.title(),ad.description(),ad.price(),ad.category()))
            .retrieve().body(Response.class);
        if(result==null||result.status()==null)throw new IllegalStateException("Moderation response is empty");
        String status=result.status().toLowerCase();if(!status.equals("approved")&&!status.equals("rejected"))throw new IllegalStateException("Unknown moderation status");
        String reason=result.reason()!=null?result.reason():result.rejection_reason();
        return new Decision(status,reason);
    }
    public record Request(String title,String description,BigDecimal price,String category){ }
    public record Response(String status,String reason,String rejection_reason){ }
    public record Decision(String status,String rejectionReason){ }
}
