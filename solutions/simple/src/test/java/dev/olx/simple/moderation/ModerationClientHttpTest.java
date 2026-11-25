package dev.olx.simple.moderation;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ModerationClientHttpTest {
    @Test void sendsChallengeFieldsAndMapsModerationResponse() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/moderation/check", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"status\":\"rejected\",\"rejection_reason\":\"policy\"}"
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var client = new ModerationClient("http://127.0.0.1:" + server.getAddress().getPort(), 500, 500);
            var result = client.check("Título válido", "Descrição", new java.math.BigDecimal("20.00"), "eletronicos");

            assertEquals("rejected", result.status());
            assertEquals("policy", result.rejectionReason());
            assertTrue(requestBody.get().contains("\"title\":\"Título válido\""));
            assertTrue(requestBody.get().contains("\"category\":\"eletronicos\""));
            assertFalse(requestBody.get().contains("images"));
        } finally {
            server.stop(0);
        }
    }
}
