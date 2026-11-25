package dev.olx.ads.web;

import dev.olx.ads.application.AdNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentNotValidException.class,ConstraintViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST) public Map<String,Object> badRequest(Exception e){return Map.of("timestamp",Instant.now(),"status",400,"error","bad_request","message",e.getMessage()==null?"Invalid request":e.getMessage());}
    @ExceptionHandler(AdNotFoundException.class) @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String,Object> notFound(AdNotFoundException e){return Map.of("timestamp",Instant.now(),"status",404,"error","not_found","message",e.getMessage());}
}
