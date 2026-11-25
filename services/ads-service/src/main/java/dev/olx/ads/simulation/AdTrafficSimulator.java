
package dev.olx.ads.simulation;

import dev.olx.ads.web.CreateAdRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(
    name = "app.simulation.enabled",
    havingValue = "true"
)
public class AdTrafficSimulator {

    private static final Logger log =
        LoggerFactory.getLogger(AdTrafficSimulator.class);

    private static final int REQUESTS_PER_BATCH = 50;
    private static final int MAX_CONCURRENT_REQUESTS = 5;

    private static final List<String> PRODUCTS = List.of(
        "Notebook Dell Inspiron",
        "Smartphone Samsung Galaxy",
        "Monitor LG Ultrawide",
        "Teclado Mecanico Gamer",
        "Notebook Lenovo ThinkPad",
        "Tablet Samsung Galaxy",
        "Camera Canon EOS",
        "Smartwatch Samsung Galaxy"
    );

    private final RestClient restClient;
    private final AtomicLong sequence = new AtomicLong();

    public AdTrafficSimulator(
        RestClient.Builder builder,
        @Value("${app.simulation.base-url:http://localhost:8082}")
        String baseUrl
    ) {
        this.restClient = builder
            .baseUrl(baseUrl)
            .build();
    }

    @Scheduled(
        fixedDelayString = "${app.simulation.interval-ms:120000}",
        initialDelayString = "${app.simulation.initial-delay-ms:10000}"
    )
    public void generateBatch() {

        String batchId = UUID.randomUUID().toString();

        log.info(
            "SIMULATOR | iniciando lote={} | requisicoes={}",
            batchId,
            REQUESTS_PER_BATCH
        );

        AtomicLong successful = new AtomicLong();
        AtomicLong failed = new AtomicLong();

        Semaphore semaphore =
            new Semaphore(MAX_CONCURRENT_REQUESTS);

        try (var executor =
            Executors.newVirtualThreadPerTaskExecutor()) {

            for (int i = 0; i < REQUESTS_PER_BATCH; i++) {

                executor.submit(() -> {

                    boolean acquired = false;

                    try {
                        semaphore.acquire();
                        acquired = true;

                        sendAd(batchId);
                        successful.incrementAndGet();

                    } catch (InterruptedException exception) {

                        Thread.currentThread().interrupt();
                        failed.incrementAndGet();

                    } catch (Exception exception) {

                        failed.incrementAndGet();

                        log.error(
                            "SIMULATOR | falha no lote={} | erro={}",
                            batchId,
                            exception.getMessage()
                        );

                    } finally {
                        if (acquired) {
                            semaphore.release();
                        }
                    }
                });
            }
        }

        log.info(
            "SIMULATOR | lote={} | sucesso={} | falhas={}",
            batchId,
            successful.get(),
            failed.get()
        );
    }

    private void sendAd(String batchId) {

        long number = sequence.incrementAndGet();

        String uniqueId = UUID.randomUUID().toString();

        String product = PRODUCTS.get(
            (int) (number % PRODUCTS.size())
        );

        BigDecimal price = BigDecimal.valueOf(
            100 + (number % 4900),
            0
        );

        CreateAdRequest request = new CreateAdRequest(
            product + " " + number + " " +
                uniqueId.substring(0, 8),

            "Anuncio sintetico OLX Enterprise. " +
                "Lote: " + batchId +
                ". Identificador: " + uniqueId +
                ". Criado em: " + Instant.now(),

            price,

            "eletronicos",

            List.of(
                "https://example.com/images/" +
                    uniqueId + ".jpg"
            )
        );

        String response = restClient.post()
            .uri("/ads")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", uniqueId)
            .body(request)
            .retrieve()
            .body(String.class);

        log.info(
            "SIMULATOR | anuncio={} | lote={} | resposta={}",
            number,
            batchId,
            response
        );
    }
}
