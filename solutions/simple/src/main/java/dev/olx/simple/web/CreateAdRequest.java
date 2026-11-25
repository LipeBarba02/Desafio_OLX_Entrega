package dev.olx.simple.web;

import dev.olx.simple.domain.Category;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

public record CreateAdRequest(
    @NotBlank @Size(min = 10, max = 100) String title,
    @NotBlank String description,
    @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal price,
    @NotBlank String category,
    @NotEmpty List<@NotBlank String> images
) {
    public Category parsedCategory() { return Category.fromCode(category.strip()); }
    public List<String> normalizedImages() {
        return images.stream().map(CreateAdRequest::normalizeImage).toList();
    }
    private static String normalizeImage(String value) {
        String normalized=value.strip();
        URI uri = URI.create(normalized);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
            || uri.getHost() == null) {
            throw new IllegalArgumentException("Each image must be an absolute HTTP or HTTPS URL");
        }
        return normalized;
    }
}
