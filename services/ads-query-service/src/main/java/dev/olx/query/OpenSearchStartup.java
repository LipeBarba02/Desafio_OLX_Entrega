package dev.olx.query;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration public class OpenSearchStartup {
    @Bean ApplicationRunner ensureIndex(OpenSearchClient client){return args->client.ensureIndex();}
}
