package dev.olx.ads.web;

import dev.olx.ads.domain.AdDraft;
import dev.olx.ads.domain.Category;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record CreateAdRequest(@NotBlank @Size(min=10,max=100) String title,
                              @NotBlank String description,
                              @NotNull @DecimalMin(value="0.0", inclusive=false) BigDecimal price,
                              @NotBlank String category,
                              @NotEmpty List<@NotBlank String> images) {
    public AdDraft toDraft() { return new AdDraft(title, description, price, Category.fromCode(category.strip()), images); }
}
