package dev.olx.query;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class AdsCommandClient {
    private final RestClient client;
    public AdsCommandClient(@Value("${ads.command.url}") String url,@Value("${ads.command.connect-timeout-ms:500}") int connectMs,
                            @Value("${ads.command.read-timeout-ms:1500}") int readMs){
        SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(connectMs);factory.setReadTimeout(readMs);
        client=RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    public AdDetailResponse getDetail(java.util.UUID id){
        try{return client.get().uri("/ads/{id}",id).retrieve().body(AdDetailResponse.class);}
        catch(HttpClientErrorException.NotFound missing){return null;}
    }
}
