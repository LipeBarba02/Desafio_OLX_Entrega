package dev.olx.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** HTTP adapter for the OpenSearch index used by the read model. */
@Component
public class OpenSearchClient {
    private final RestClient http;
    private final ObjectMapper json;
    private final String index;

    public OpenSearchClient(
        @Value("${opensearch.url}") String url,
        @Value("${opensearch.index:ads-v1}") String index,
        @Value("${opensearch.connect-timeout-ms:1000}") int connectTimeoutMs,
        @Value("${opensearch.read-timeout-ms:2000}") int readTimeoutMs,
        ObjectMapper json
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        this.http = RestClient.builder()
            .baseUrl(url)
            .requestFactory(requestFactory)
            .build();
        this.index = index;
        this.json = json;
    }

    /** Creates the versioned index and explicit field types on first startup. */
    public void ensureIndex() {
        HttpStatusCode currentStatus = http.get()
            .uri("/{index}", index)
            .exchange((request, response) -> response.getStatusCode());
        if (currentStatus.is2xxSuccessful()) {
            return;
        }

        Map<String, Object> properties = Map.of(
            "adId", Map.of("type", "keyword"),
            "title", Map.of("type", "text"),
            "description", Map.of("type", "text"),
            "category", Map.of("type", "keyword"),
            "price", Map.of("type", "scaled_float", "scaling_factor", 100),
            "createdAt", Map.of("type", "date")
        );
        Map<String, Object> definition = Map.of("mappings", Map.of("properties", properties));

        http.put()
            .uri("/{index}", index)
            .body(definition)
            .retrieve()
            .toBodilessEntity();
    }

    /** PUT by adId makes replay of an approved decision converge to one document. */
    public void upsert(SearchDocument document) {
        http.put()
            .uri("/{index}/_doc/{id}", index, document.adId())
            .body(document)
            .retrieve()
            .toBodilessEntity();
    }

    /** Delete is idempotent: a missing document already represents the requested final state. */
    public void delete(UUID id) {
        try {
            http.delete()
                .uri("/{index}/_doc/{id}", index, id)
                .retrieve()
                .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound ignored) {
            // No index document is equivalent to a deleted/rejected listing.
        }
    }

    public SearchDocument find(UUID id) {
        try {
            JsonNode response = http.get()
                .uri("/{index}/_doc/{id}", index, id)
                .retrieve()
                .body(JsonNode.class);
            if (response == null || !response.path("found").asBoolean()) {
                return null;
            }
            return json.treeToValue(response.path("_source"), SearchDocument.class);
        } catch (HttpClientErrorException.NotFound missing) {
            return null;
        } catch (Exception failure) {
            throw new IllegalStateException("Could not read OpenSearch document", failure);
        }
    }

    /** Executes match-all or multi-match plus an optional exact category filter. */
    public SearchPage search(String text, String category, int size, String cursor) {
        List<Map<String, Object>> filters = new ArrayList<>();
        if (category != null && !category.isBlank()) {
            filters.add(Map.of("term", Map.of("category", category)));
        }

        Map<String, Object> textQuery = text == null || text.isBlank()
            ? Map.of("match_all", Map.of())
            : Map.of("multi_match", Map.of(
                "query", text,
                "fields", List.of("title^3", "description")
            ));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("size", size);
        request.put("query", Map.of("bool", Map.of(
            "must", List.of(textQuery),
            "filter", filters
        )));
        request.put("sort", List.of(
            Map.of("createdAt", Map.of("order", "desc")),
            Map.of("adId", Map.of("order", "asc"))
        ));
        if (cursor != null && !cursor.isBlank()) {
            request.put("search_after", decodeCursor(cursor));
        }

        JsonNode response = http.post()
            .uri("/{index}/_search", index)
            .body(request)
            .retrieve()
            .body(JsonNode.class);
        if (response == null) {
            throw new IllegalStateException("OpenSearch returned an empty result");
        }

        JsonNode hits = response.path("hits").path("hits");
        List<SearchDocument> items = new ArrayList<>();
        List<JsonNode> lastSortValues = List.of();
        for (JsonNode hit : hits) {
            try {
                items.add(json.treeToValue(hit.path("_source"), SearchDocument.class));
            } catch (Exception invalidDocument) {
                throw new IllegalStateException("OpenSearch returned an invalid ad document", invalidDocument);
            }

            JsonNode sort = hit.path("sort");
            if (sort.isArray()) {
                List<JsonNode> values = new ArrayList<>();
                sort.forEach(values::add);
                lastSortValues = values;
            }
        }

        JsonNode totalNode = response.path("hits").path("total");
        long total = totalNode.path("value").asLong(items.size());
        String totalRelation = totalNode.path("relation").asText("eq");
        String nextCursor = items.size() == size && !lastSortValues.isEmpty()
            ? encodeCursor(lastSortValues)
            : null;

        return new SearchPage(List.copyOf(items), total, totalRelation, nextCursor);
    }

    private String encodeCursor(List<JsonNode> sortValues) {
        try {
            byte[] jsonBytes = json.writeValueAsBytes(sortValues);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(jsonBytes);
        } catch (Exception failure) {
            throw new IllegalStateException("Could not encode search cursor", failure);
        }
    }

    private List<Object> decodeCursor(String cursor) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            JsonNode values = json.readTree(decoded);
            if (!values.isArray() || values.size() != 2) {
                throw new IllegalArgumentException("Cursor must contain the two sort values");
            }
            List<Object> result = new ArrayList<>(values.size());
            for (JsonNode value : values) {
                result.add(json.treeToValue(value, Object.class));
            }
            return result;
        } catch (IllegalArgumentException invalidCursor) {
            throw invalidCursor;
        } catch (Exception invalidCursor) {
            throw new IllegalArgumentException("Invalid search cursor", invalidCursor);
        }
    }
}
