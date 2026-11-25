package dev.olx.simple.web;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CreateAdRequestTest {
    @Test void stripsImageUrlAndPreservesOrder() {
        var request = new CreateAdRequest("Notebook usado em bom estado", "Descrição", new BigDecimal("100.00"),
            "eletronicos", List.of(" https://example.com/a.jpg "));
        assertEquals(List.of("https://example.com/a.jpg"), request.normalizedImages());
    }
    @Test void rejectsRelativeImageUrl() {
        var request = new CreateAdRequest("Notebook usado em bom estado", "Descrição", new BigDecimal("100.00"),
            "eletronicos", List.of("/image.jpg"));
        assertThrows(IllegalArgumentException.class, request::normalizedImages);
    }
}
