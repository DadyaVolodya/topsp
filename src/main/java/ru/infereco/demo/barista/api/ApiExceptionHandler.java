package ru.infereco.demo.barista.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.infereco.demo.barista.chat.SessionNotFoundException;
import ru.infereco.demo.barista.stt.SttNotReadyException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(SessionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> notFound(SessionNotFoundException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badArg(IllegalArgumentException ex) {
        return Map.of("error", ex.getMessage() == null ? "bad request" : ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(MethodArgumentNotValidException ex) {
        return Map.of("error", "text is required");
    }

    @ExceptionHandler(SttNotReadyException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, String> stt(SttNotReadyException ex) {
        return Map.of("error", "распознавание ещё не готово: " + ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> upstream(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", ex.getMessage() == null ? "model call failed" : ex.getMessage()));
    }
}
