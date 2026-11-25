package dev.olx.ads.domain;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Objects;

/** Immutable validated domain input. It contains no Spring or JPA types. */
public record AdDraft(String title, String description, BigDecimal price, Category category, List<String> images) {
    public AdDraft {
        title = requiredText(title, "title");
        description = requiredText(description, "description");
        Objects.requireNonNull(price, "price is required");
        Objects.requireNonNull(category, "category is required");
        if (title.length() < 10 || title.length() > 100) throw new IllegalArgumentException("title must have between 10 and 100 characters");
        if (price.signum() <= 0) throw new IllegalArgumentException("price must be positive");
        BigDecimal normalizedPrice = price.stripTrailingZeros();
        if (normalizedPrice.scale() > 2 || normalizedPrice.precision() - normalizedPrice.scale() > 17) {
            throw new IllegalArgumentException("price must fit NUMERIC(19,2)");
        }
        images = Objects.requireNonNull(images, "images are required").stream().map(AdDraft::normalizeImage).toList();
        if (images.isEmpty()) throw new IllegalArgumentException("at least one image is required");
    }
    private static String requiredText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field + " is required").strip();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
    private static String normalizeImage(String value) {
        String normalized = requiredText(value, "image");
        URI uri = URI.create(normalized);
        boolean http = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
        if (!http || uri.getHost() == null) throw new IllegalArgumentException("image must be an absolute HTTP or HTTPS URL with a host");
        return normalized;
    }
}
