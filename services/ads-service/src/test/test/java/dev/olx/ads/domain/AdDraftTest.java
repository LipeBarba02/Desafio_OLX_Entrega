package dev.olx.ads.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdDraftTest {
    @Test void trimsTextAndCopiesNormalizedImageList(){
        var draft=new AdDraft(" Notebook usado em bom estado "," Descrição ",new BigDecimal("1.00"),Category.ELECTRONICS,List.of(" https://img.example/a.jpg "));
        assertEquals("Notebook usado em bom estado",draft.title());assertEquals("Descrição",draft.description());assertEquals(List.of("https://img.example/a.jpg"),draft.images());
    }
    @Test void rejectsNonPositivePrice(){assertThrows(IllegalArgumentException.class,()->new AdDraft("Notebook usado em bom estado","Descrição",BigDecimal.ZERO,Category.ELECTRONICS,List.of("https://img.example/a.jpg")));}
    @Test void rejectsPriceWithMoreThanTwoMeaningfulDecimals(){assertThrows(IllegalArgumentException.class,()->new AdDraft("Notebook usado em bom estado","Descrição",new BigDecimal("1.001"),Category.ELECTRONICS,List.of("https://img.example/a.jpg")));}
    @Test void rejectsRelativeImageUrl(){assertThrows(IllegalArgumentException.class,()->new AdDraft("Notebook usado em bom estado","Descrição",BigDecimal.ONE,Category.ELECTRONICS,List.of("/a.jpg")));}
}
