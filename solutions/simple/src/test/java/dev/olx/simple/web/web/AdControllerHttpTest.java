package dev.olx.simple.web.web;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.olx.simple.application.AdNotFoundException;
import dev.olx.simple.application.AdService;
import dev.olx.simple.domain.ModerationStatus;
import dev.olx.simple.web.AdController;
import dev.olx.simple.web.AdResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdController.class)
class AdControllerHttpTest {
    @Autowired MockMvc mvc;
    @MockBean AdService service;

    @Test void createReturns201AndLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any())).thenReturn(new AdResponse(id, "Notebook usado em bom estado",
            "Descrição", new BigDecimal("100.00"), "eletronicos", List.of("https://example.com/a.jpg"),
            ModerationStatus.APPROVED, null, Instant.parse("2026-11-24T12:00:00Z")));

        mvc.perform(post("/ads").contentType(MediaType.APPLICATION_JSON).content(request()))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/ads/" + id))
            .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test void beanValidationRejectsShortTitleBeforeCallingService() throws Exception {
        String invalid = request().replace("Notebook usado em bom estado", "short");

        mvc.perform(post("/ads").contentType(MediaType.APPLICATION_JSON).content(invalid))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test void missingAdIsMappedTo404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenThrow(new AdNotFoundException(id));

        mvc.perform(get("/ads/" + id)).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("not_found"));
    }

    private static String request() {
        return """
            {"title":"Notebook usado em bom estado","description":"Descrição detalhada",
             "price":100.00,"category":"eletronicos","images":["https://example.com/a.jpg"]}
            """;
    }
}
