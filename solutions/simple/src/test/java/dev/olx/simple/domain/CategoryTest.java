package dev.olx.simple.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CategoryTest {
    @Test void mapsOfficialCategoryCode() {
        assertEquals(Category.ELECTRONICS, Category.fromCode("eletronicos"));
    }
    @Test void rejectsUnknownCode() {
        assertThrows(IllegalArgumentException.class, () -> Category.fromCode("unknown"));
    }
}
