package dev.olx.query;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
public class QueryExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,Object> invalid(IllegalArgumentException e){return Map.of("timestamp",Instant.now(),"status",400,"error","bad_request","message",e.getMessage()==null?"Invalid query":e.getMessage());}
    @ExceptionHandler(RestClientException.class) @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String,Object> dependency(RestClientException e){return Map.of("timestamp",Instant.now(),"status",503,"error","query_dependency_unavailable","message","A read dependency is unavailable; retry later.");}
    @ExceptionHandler(IllegalStateException.class) @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String,Object> readModelFailure(IllegalStateException e){return Map.of("timestamp",Instant.now(),"status",503,"error","read_model_unavailable","message","The read model is temporarily unavailable; retry later.");}
}
