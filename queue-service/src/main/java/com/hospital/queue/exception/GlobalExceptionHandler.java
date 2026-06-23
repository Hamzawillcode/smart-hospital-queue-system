package com.hospital.queue.exception;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LogManager.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(QueueEntryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            QueueEntryNotFoundException ex) {
        log.warn("QueueEntryNotFoundException: {}",
                ex.getMessage());
        return new ResponseEntity<>(
                new ErrorResponse(404, ex.getMessage(),
                        LocalDateTime.now()),
                HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(PatientAlreadyInQueueException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyInQueue(
            PatientAlreadyInQueueException ex) {
        log.warn("PatientAlreadyInQueueException: {}",
                ex.getMessage());
        return new ResponseEntity<>(
                new ErrorResponse(409, ex.getMessage(),
                        LocalDateTime.now()),
                HttpStatus.CONFLICT);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> err.getField()
                        + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        log.warn("Validation failed: {}", message);
        return new ResponseEntity<>(
                new ErrorResponse(400, message,
                        LocalDateTime.now()),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex) {
        log.error("Unexpected error: {}",
                ex.getMessage(), ex);
        return new ResponseEntity<>(
                new ErrorResponse(500,
                        "Something went wrong: "
                                + ex.getMessage(),
                        LocalDateTime.now()),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
