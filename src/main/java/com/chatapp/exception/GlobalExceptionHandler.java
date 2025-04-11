package com.chatapp.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.chatapp.dto.response.ApiResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        ApiResponse<Object> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setMessage(ex.getMessage());
        response.setError("Resource not found");

        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Object>> handleResourceAlreadyExistsException(ResourceAlreadyExistsException ex) {
        ApiResponse<Object> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setMessage(ex.getMessage());
        response.setError("Resource already exists");

        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadRequestException(BadRequestException ex) {
        ApiResponse<Object> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setMessage(ex.getMessage());
        response.setError("Bad request");

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnauthorizedException(UnauthorizedException ex) {
        ApiResponse<Object> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setMessage(ex.getMessage());
        response.setError("Unauthorized");

        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        List<String> errorMessages = new ArrayList<>();

        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
            errorMessages.add(errorMessage);
        });

        ApiResponse<Map<String, String>> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setMessage("Lỗi xác thực dữ liệu");
        response.setPayload(errors);
        response.setErrors(errorMessages);

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGlobalException(Exception ex) {
        ApiResponse<Object> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setMessage("Đã xảy ra lỗi không mong muốn: " + ex.getMessage());
        response.setError("Internal server error");

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(OtpRateLimitException.class)
    public ResponseEntity<ApiResponse<Object>> handleOtpRateLimitException(OtpRateLimitException e) {
        ApiResponse<Object> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setMessage(e.getMessage());
        response.setError("Rate limit exceeded");

        return new ResponseEntity<>(response, HttpStatus.TOO_MANY_REQUESTS);
    }
}