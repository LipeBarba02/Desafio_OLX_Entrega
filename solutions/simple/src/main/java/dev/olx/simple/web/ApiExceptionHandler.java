package dev.olx.simple.web;

import dev.olx.simple.application.AdNotFoundException;
import dev.olx.simple.moderation.ModerationUnavailableException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badRequest(Exception exception) {
        return Map.of("timestamp", Instant.now(), "status", 400, "error", "bad_request", "message", safeMessage(exception));
    }

    @ExceptionHandler(AdNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> notFound(AdNotFoundException exception) {
        return Map.of("timestamp", Instant.now(), "status", 404, "error", "not_found", "message", exception.getMessage());
    }

    @ExceptionHandler({ModerationUnavailableException.class, RestClientException.class})
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, Object> moderationUnavailable(Exception exception) {
        return Map.of("timestamp", Instant.now(), "status", 502, "error", "moderation_unavailable",
            "message", "The moderation service did not return a usable response; retry the request later.");
    }

    private String safeMessage(Exception e) { return e.getMessage() == null ? "Invalid request" : e.getMessage(); }
}
