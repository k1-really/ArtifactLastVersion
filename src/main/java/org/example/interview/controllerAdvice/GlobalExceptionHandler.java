package org.example.interview.controllerAdvice;


import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Глобальный обработчик ошибок для всего приложения
 * @RestControllerAdvice перехватывает исключения из всех контроллеров
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Обработка ошибок валидации параметров
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {

        log.error("Validation error: {}", ex.getMessage());
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid request parameters",
                ex.getMessage()
        );
    }

    /**
     * Обработка ошибок, когда артефакт не найден
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(
            RuntimeException ex, WebRequest request) {

        log.error("Runtime error: {}", ex.getMessage());

        // Проверяем сообщение об ошибке для определения статуса
        if (ex.getMessage() != null && ex.getMessage().contains("not found")) {
            return buildErrorResponse(
                    HttpStatus.NOT_FOUND,
                    "Artifact not found",
                    ex.getMessage()
            );
        }

        if (ex.getMessage() != null &&
                (ex.getMessage().contains("timeout") || ex.getMessage().contains("unavailable"))) {
            return buildErrorResponse(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Repository unavailable",
                    ex.getMessage()
            );
        }

        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                ex.getMessage()
        );
    }

    /**
     * Вспомогательный метод для построения стандартного ответа об ошибке
     */
    private ResponseEntity<Object> buildErrorResponse(
            HttpStatus status, String title, String detail) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("title", title);
        body.put("detail", detail);

        return new ResponseEntity<>(body, status);
    }
}
