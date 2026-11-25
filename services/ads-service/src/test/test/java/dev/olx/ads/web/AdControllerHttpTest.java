package dev.olx.ads.web;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.olx.ads.application.AdNotFoundException;
import dev.olx.ads.application.AdQueryService;
import dev.olx.ads.application.AdSubmissionResult;
import dev.olx.ads.application.AdSubmissionService;
import dev.olx.ads.domain.AdStatus;
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
    @MockBean AdSubmissionService submission;
    @MockBean AdQueryService query;

    @Test void createReturnsAcceptedWithPendingStatusAndLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(submission.submit(any())).thenReturn(new AdSubmissionResult(id, AdStatus.PENDING_MODERATION));

        mvc.perform(post("/ads").contentType(MediaType.APPLICATION_JSON).content(request()))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Location", "/ads/" + id))
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.status").value("PENDING_MODERATION"));
    }

    @Test void listBindsNamedQueryParametersAndReturnsPage() throws Exception {
        when(query.list("eletronicos", 0, 20)).thenReturn(Page.empty());

        mvc.perform(get("/ads").param("page", "0").param("size", "20").param("category", "eletronicos"))
            .andExpect(status().isOk());

        verify(query).list("eletronicos", 0, 20);
    }

    @Test void beanValidationRejectsInvalidSubmissionBeforeUseCase() throws Exception {
        String invalid = request().replace("Notebook usado em bom estado", "short");

        mvc.perform(post("/ads").contentType(MediaType.APPLICATION_JSON).content(invalid))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(submission);
    }

    @Test void missingCommandReadReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(query.get(id)).thenThrow(new AdNotFoundException(id));

        mvc.perform(get("/ads/" + id)).andExpect(status().isNotFound());
    }

    private static String request() {
        return """
            {"title":"Notebook usado em bom estado","description":"Descrição detalhada",
             "price":100.00,"category":"eletronicos","images":["https://example.com/a.jpg"]}
            """;
    }
}
