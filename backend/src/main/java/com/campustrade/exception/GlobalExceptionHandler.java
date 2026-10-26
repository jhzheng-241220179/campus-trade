package com.campustrade.exception;
import com.campustrade.exception.ProductNotFoundException;
import com.campustrade.exception.ProductStatusException;
import com.campustrade.exception.ProductConflictException;
import com.campustrade.common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import org.springframework.web.servlet.resource.NoResourceFoundException;
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            IllegalArgumentException e) {

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(400, e.getMessage()));
    }
    @ExceptionHandler({
        ProductStatusException.class,
        ProductConflictException.class
})
@ResponseStatus(HttpStatus.CONFLICT)
public ApiResponse<Void> handleProductConflict(RuntimeException ex) {
    return new ApiResponse<>(409, ex.getMessage(), null);
}
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException e) {

        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求参数不合法");

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(400, message));
    }
    @ExceptionHandler(NoResourceFoundException.class)
        @ResponseStatus(HttpStatus.NOT_FOUND)
        public ApiResponse<Void> handleNotFound(
                NoResourceFoundException ex) {

        return new ApiResponse<>(
                404,
                "请求的资源不存在",
                null
        );
        }
        
        @ExceptionHandler(ProductNotFoundException.class)
        @ResponseStatus(HttpStatus.NOT_FOUND)
        public ApiResponse<Void> handleProductNotFound(
                ProductNotFoundException ex) {

        return new ApiResponse<>(404, ex.getMessage(), null);
        }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception e) {

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "服务器内部错误"));
    }
        @ExceptionHandler(EmailAlreadyRegisteredException.class)
        @ResponseStatus(HttpStatus.CONFLICT)
        public ApiResponse<Void> handleEmailAlreadyRegistered(
                EmailAlreadyRegisteredException ex) {

        return new ApiResponse<>(409, ex.getMessage(), null);
        }
}